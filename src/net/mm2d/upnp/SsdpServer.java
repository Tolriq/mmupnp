/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * SSDPパケットの受信を行うクラスの親クラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
abstract class SsdpServer {
    /**
     * SSDPに使用するアドレス。
     */
    public static final String SSDP_ADDR = "239.255.255.250";
    /**
     * SSPDに使用するポート番号
     */
    public static final int SSDP_PORT = 1900;
    private static final InetSocketAddress SSDP_SO_ADDR =
            new InetSocketAddress(SSDP_ADDR, SSDP_PORT);
    private static final InetAddress SSDP_INET_ADDR = SSDP_SO_ADDR.getAddress();
    private static final String TAG = "SsdpServer";
    private final NetworkInterface mInterface;
    private InterfaceAddress mInterfaceAddress;
    private final int mBindPort;
    private DatagramChannel mChannel;
    private ReceiveThread mThread;

    /**
     * 使用するインターフェースを指定してインスタンス作成。
     *
     * 使用するポートは自動割当となる。
     *
     * @param ni 使用するインターフェース
     */
    public SsdpServer(@Nonnull NetworkInterface ni) {
        this(ni, 0);
    }

    /**
     * 使用するインターフェースとポート指定してインスタンス作成。
     *
     * @param ni 使用するインターフェース
     * @param bindPort 使用するポート
     */
    public SsdpServer(@Nonnull NetworkInterface ni, int bindPort) {
        mBindPort = bindPort;
        mInterface = ni;
        final List<InterfaceAddress> ifas = mInterface.getInterfaceAddresses();
        for (final InterfaceAddress ifa : ifas) {
            if (ifa.getAddress() instanceof Inet4Address) {
                mInterfaceAddress = ifa;
                break;
            }
        }
        if (mInterfaceAddress == null) {
            throw new IllegalArgumentException("ni does not have IPv4 address.");
        }
    }

    /**
     * ソケットのオープンを行う。
     *
     * @throws IOException ソケット作成に失敗
     */
    public void open() throws IOException {
        if (mChannel != null) {
            close();
        }
        mChannel = DatagramChannel.open(StandardProtocolFamily.INET);
        mChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        mChannel.bind(new InetSocketAddress(mBindPort));
        mChannel.setOption(StandardSocketOptions.IP_MULTICAST_IF, mInterface);
        mChannel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 4);
    }

    /**
     * ソケットのクローズを行う
     */
    public void close() {
        stop(true);
        if (mChannel != null) {
            try {
                mChannel.close();
            } catch (final IOException ignored) {
            }
            mChannel = null;
        }
    }

    /**
     * 受信スレッドの開始を行う。
     */
    public void start() {
        if (mThread != null) {
            stop(false);
        }
        mThread = new ReceiveThread();
        mThread.start();
    }

    /**
     * 受信スレッドの停止を行う。
     */
    public void stop() {
        stop(false);
    }

    /**
     * 受信スレッドの停止と必要があればJoinを行う。
     *
     * 現在の実装ではIO待ちに割り込むことはできないため、
     * joinを指定しても偶然ソケットタイムアウトやソケット受信が発生しないかぎりjoinできない。
     * TODO: SocketChannelを使用した受信(MulticastChannelはAndroid N以降のため保留)
     *
     * @param join trueの時スレッドのJoin待ちを行う。
     */
    public void stop(boolean join) {
        if (mThread == null) {
            return;
        }
        mThread.shutdownRequest();
        if (join) {
            try {
                mThread.join(1000);
            } catch (final InterruptedException ignored) {
            }
            mThread = null;
        }
    }

    /**
     * このソケットを使用してメッセージ送信を行う。
     *
     * @param message 送信するメッセージ
     */
    public void send(@Nonnull SsdpMessage message) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            message.getMessage().writeData(baos);
            send(baos.toByteArray());
        } catch (final IOException e) {
            Log.w(TAG, e);
        }
    }

    /**
     * このソケットを使用してメッセージ送信を行う。
     *
     * @param message 送信するメッセージ
     */
    public void send(@Nonnull byte[] message) {
        try {
            mChannel.send(ByteBuffer.wrap(message), SSDP_SO_ADDR);
        } catch (final IOException e) {
            Log.w(TAG, e);
        }
    }

    /**
     * メッセージ受信後の処理、小クラスにより実装する。
     *
     * @param addr 受信したインターフェース
     * @param pa 受信したパケットの送信元アドレス
     * @param message 受信したデータ
     */
    protected abstract void onReceive(@Nonnull InterfaceAddress ifa, @Nonnull InetAddress pa,
            @Nonnull byte[] data);

    /**
     * Joinを行う。
     *
     * 特定ポートにBindしていない（マルチキャスト受信ソケットでない）場合は何も行わない
     *
     * @throws IOException Joinコールにより発生
     */
    private void joinGroup() throws IOException {
        if (mBindPort != 0) {
            mChannel.join(SSDP_INET_ADDR, mInterface);
        }
    }

    private class ReceiveThread extends Thread {
        private volatile boolean mShutdownRequest;

        /**
         * インスタンス作成
         */
        public ReceiveThread() {
            super("ReceiveThread");
        }

        /**
         * 割り込みを行い、スレッドを終了させる。
         */
        public void shutdownRequest() {
            mShutdownRequest = true;
            interrupt();
        }

        @Override
        public void run() {
            try {
                joinGroup();
                final ByteBuffer buffer = ByteBuffer.allocate(1500);
                while (!mShutdownRequest) {
                    try {
                        final SocketAddress sa = mChannel.receive(buffer);
                        if (mShutdownRequest) {
                            break;
                        }
                        final InetAddress ia = ((InetSocketAddress) sa).getAddress();
                        final byte[] data = new byte[buffer.position()];
                        buffer.flip();
                        buffer.get(data);
                        onReceive(mInterfaceAddress, ia, data);
                        buffer.clear();
                    } catch (final SocketTimeoutException ignored) {
                    }
                }
            } catch (final IOException ignored) {
            }
        }
    }
}

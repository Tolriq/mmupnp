/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.IoUtils;
import net.mm2d.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * SSDPパケットの受信を行うクラスの親クラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
// TODO: SocketChannelを使用した受信(MulticastChannelはAndroid N以降のため保留)
abstract class SsdpServer {
    private static final String TAG = SsdpServer.class.getSimpleName();
    /**
     * SSDPに使用するアドレス。
     */
    public static final String SSDP_ADDR = "239.255.255.250";
    /**
     * SSDPに使用するポート番号
     */
    public static final int SSDP_PORT = 1900;
    private static final InetSocketAddress SSDP_SO_ADDR = new InetSocketAddress(SSDP_ADDR, SSDP_PORT);
    private static final InetAddress SSDP_INET_ADDR = SSDP_SO_ADDR.getAddress();
    @Nonnull
    private final NetworkInterface mInterface;
    @Nonnull
    private final InterfaceAddress mInterfaceAddress;
    private final int mBindPort;
    private MulticastSocket mSocket;
    private ReceiveTask mReceiveTask;

    /**
     * 使用するインターフェースを指定してインスタンス作成。
     *
     * <p>使用するポートは自動割当となる。
     *
     * @param networkInterface 使用するインターフェース
     */
    public SsdpServer(@Nonnull NetworkInterface networkInterface) {
        this(networkInterface, 0);
    }

    /**
     * 使用するインターフェースとポート指定してインスタンス作成。
     *
     * @param networkInterface 使用するインターフェース
     * @param bindPort         使用するポート
     */
    public SsdpServer(@Nonnull NetworkInterface networkInterface, int bindPort) {
        mInterfaceAddress = findInet4Address(networkInterface);
        mBindPort = bindPort;
        mInterface = networkInterface;
    }

    private static InterfaceAddress findInet4Address(NetworkInterface networkInterface) {
        final List<InterfaceAddress> addressList = networkInterface.getInterfaceAddresses();
        for (final InterfaceAddress address : addressList) {
            if (address.getAddress() instanceof Inet4Address) {
                return address;
            }
        }
        throw new IllegalArgumentException("ni does not have IPv4 address.");
    }

    /**
     * BindされたInterfaceのアドレスを返す。
     *
     * @return BindされたInterfaceのアドレス
     */
    @Nonnull
    protected InterfaceAddress getInterfaceAddress() {
        return mInterfaceAddress;
    }

    /**
     * ソケットのオープンを行う。
     *
     * @throws IOException ソケット作成に失敗
     */
    public void open() throws IOException {
        if (mSocket == null) {
            close();
        }
        mSocket = createMulticastSocket(mBindPort);
        mSocket.setNetworkInterface(mInterface);
        mSocket.setTimeToLive(4);
    }

    // VisibleForTesting
    MulticastSocket createMulticastSocket(int port) throws IOException {
        return new MulticastSocket(port);
    }

    /**
     * ソケットのクローズを行う
     */
    public void close() {
        stop(false);
        IoUtils.closeQuietly(mSocket);
        mSocket = null;
    }

    /**
     * 受信スレッドの開始を行う。
     */
    public void start() {
        if (mReceiveTask != null) {
            stop();
        }
        mReceiveTask = new ReceiveTask(this, mSocket, mBindPort);
        mReceiveTask.start();
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
     * <p>現在の実装ではIO待ちに割り込むことはできないため、
     * joinを指定しても偶然ソケットタイムアウトやソケット受信が発生しないかぎりjoinできない。
     *
     * @param join trueの時スレッドのJoin待ちを行う。
     */
    public void stop(boolean join) {
        if (mReceiveTask == null) {
            return;
        }
        mReceiveTask.shutdownRequest(join);
        mReceiveTask = null;
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
            final DatagramPacket dp = new DatagramPacket(message, message.length, SSDP_SO_ADDR);
            mSocket.send(dp);
        } catch (final IOException e) {
            Log.w(TAG, e);
        }
    }

    /**
     * メッセージ受信後の処理、サブクラスにより実装する。
     *
     * @param sourceAddress 送信元アドレス
     * @param data          受信したデータ
     * @param length        受信したデータの長さ
     */
    protected abstract void onReceive(@Nonnull InetAddress sourceAddress, @Nonnull byte[] data, int length);

    private static class ReceiveTask implements Runnable {
        private static final String TAG = ReceiveTask.class.getSimpleName();
        private final SsdpServer mSsdpServer;
        private final MulticastSocket mSocket;
        private final int mBindPort;

        private volatile boolean mShutdownRequest;
        private final Object mThreadLock = new Object();
        private Thread mThread;

        /**
         * インスタンス作成
         */
        ReceiveTask(SsdpServer ssdpServer, MulticastSocket socket, int port) {
            mSsdpServer = ssdpServer;
            mSocket = socket;
            mBindPort = port;
        }

        /**
         * スレッドを作成して処理を開始する。
         */
        void start() {
            mShutdownRequest = false;
            synchronized (mThreadLock) {
                mThread = new Thread(this, TAG);
                mThread.start();
            }
        }

        /**
         * 割り込みを行い、スレッドを終了させる。
         *
         * <p>現在はSocketを使用しているため割り込みは効果がない。
         *
         * @param join Threadのjoin待ちを行う場合はtrue
         */
        void shutdownRequest(boolean join) {
            mShutdownRequest = true;
            synchronized (mThreadLock) {
                if (mThread == null) {
                    return;
                }
                mThread.interrupt();
                if (join) {
                    try {
                        mThread.join(1000);
                    } catch (final InterruptedException ignored) {
                    }
                }
                mThread = null;
            }
        }

        @Override
        public void run() {
            if (mShutdownRequest) {
                return;
            }
            try {
                joinGroup();
                receiveLoop();
                leaveGroup();
            } catch (final IOException ignored) {
            }
        }

        /**
         * 受信処理を行う。
         *
         * @throws IOException 入出力処理で例外発生
         */
        private void receiveLoop() throws IOException {
            final byte[] buf = new byte[1500];
            while (!mShutdownRequest) {
                try {
                    final DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    mSocket.receive(dp);
                    if (mShutdownRequest) {
                        break;
                    }
                    mSsdpServer.onReceive(dp.getAddress(), dp.getData(), dp.getLength());
                } catch (final SocketTimeoutException ignored) {
                }
            }
        }

        /**
         * Joinを行う。
         *
         * <p>特定ポートにBindしていない（マルチキャスト受信ソケットでない）場合は何も行わない
         *
         * @throws IOException Joinコールにより発生
         */
        private void joinGroup() throws IOException {
            if (mBindPort != 0) {
                mSocket.joinGroup(SSDP_INET_ADDR);
            }
        }

        /**
         * Leaveを行う。
         *
         * <p>特定ポートにBindしていない（マルチキャスト受信ソケットでない）場合は何も行わない
         *
         * @throws IOException Leaveコールにより発生
         */
        private void leaveGroup() throws IOException {
            if (mBindPort != 0) {
                mSocket.leaveGroup(SSDP_INET_ADDR);
            }
        }
    }
}

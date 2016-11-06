/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * SSDP NOTIFYを受信するクラス
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
class SsdpNotifyReceiver extends SsdpServer {
    /**
     * NOTIFY受信を受け取るリスナー。
     */
    public interface NotifyListener {
        /**
         * NOTIFY受信時にコール。
         *
         * @param message 受信したNOTFYメッセージ
         */
        void onReceiveNotify(@Nonnull SsdpRequestMessage message);
    }

    private static final String TAG = "SsdpNotifyReceiver";
    private NotifyListener mListener;

    /**
     * インスタンス作成。
     *
     * @param ni 使用するインターフェース
     */
    public SsdpNotifyReceiver(@Nonnull NetworkInterface ni) {
        super(ni, SSDP_PORT);
    }

    /**
     * NOTIFY受信リスナーを登録する。
     *
     * @param listener リスナー
     */
    public void setNotifyListener(@Nullable NotifyListener listener) {
        mListener = listener;
    }

    @Override
    protected void onReceive(InterfaceAddress ifa, InetAddress ia, byte[] data) {
        // アドレス設定が間違っている場合でもマルチキャストパケットの送信はできてしまう。
        // セグメント情報が間違っており、マルチキャスト以外のやり取りができない相手からのパケットは
        // 受け取っても無駄なので破棄する。
        if (!isSameSegment(ifa, ia)) {
            Log.w(TAG, "Invalid segment packet received:" + ia.toString() + " " + ifa.toString());
            return;
        }
        try {
            final SsdpRequestMessage message = new SsdpRequestMessage(ifa, ia, data);
            // M-SEARCHパケットは無視する
            if (SsdpMessage.M_SEARCH.equals(message.getMethod())) {
                return;
            }
            if (!SsdpMessage.SSDP_BYEBYE.equals(message.getNts())
                    && !message.hasValidLocation()) {
                return;
            }
            if (mListener != null) {
                mListener.onReceiveNotify(message);
            }
        } catch (final IOException ignored) {
        }
    }

    private static boolean isSameSegment(InterfaceAddress ifa, InetAddress ia) {
        final byte[] a = ifa.getAddress().getAddress();
        final byte[] b = ia.getAddress();
        final int pref = ifa.getNetworkPrefixLength();
        final int bytes = pref / 8;
        final int bits = pref % 8;
        for (int i = 0; i < bytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        if (bits != 0) {
            final byte mask = (byte) (0xff << (8 - bits));
            if ((a[bytes] & mask) != (b[bytes] & mask)) {
                return false;
            }
        }
        return true;
    }
}

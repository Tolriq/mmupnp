/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;

import javax.annotation.Nonnull;

/**
 * SSDPリクエストメッセージを表現するクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class SsdpRequestMessage extends SsdpMessage {
    /**
     * インスタンス作成
     */
    public SsdpRequestMessage() {
        super();
    }

    /**
     * 受信した情報からインスタンス作成
     *
     * @param ifa 受信したInterfaceAddress
     * @param pa 受信したパケットの送信元アドレス
     * @param message 受信したデータ
     * @throws IOException 入出力エラー
     */
    public SsdpRequestMessage(@Nonnull InterfaceAddress ifa, @Nonnull InetAddress pa,
            @Nonnull byte[] message)
            throws IOException {
        super(ifa, pa, message);
    }

    @Override
    @Nonnull
    protected HttpMessage newMessage() {
        return new HttpRequest();
    }

    @Override
    @Nonnull
    protected HttpRequest getMessage() {
        return (HttpRequest) super.getMessage();
    }

    /**
     * リクエストメソッドを返す。
     *
     * @return リクエストメソッド
     */
    @Nonnull
    public String getMethod() {
        return getMessage().getMethod();
    }

    /**
     * リクエストメソッドを設定する。
     *
     * @param method リクエストメソッド
     */
    public void setMethod(@Nonnull String method) {
        getMessage().setMethod(method);
    }

    /**
     * URIを返す。
     *
     * @return URI文字列
     */
    @Nonnull
    public String getUri() {
        return getMessage().getUri();
    }

    /**
     * URIを設定する。
     *
     * @param uri URI文字列
     */
    public void setUri(@Nonnull String uri) {
        getMessage().setUri(uri);
    }
}

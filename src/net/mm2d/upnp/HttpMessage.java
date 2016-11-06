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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * HTTPのメッセージを表現するクラスの親クラス。
 *
 * ResponseとRequestでStart Lineのフォーマットが異なるため
 * その部分の実装は小クラスに任せている。
 *
 * UPnPの通信でよく利用される小さなデータのやり取りに特化したもので、
 * 長大なデータのやり取りは想定していない。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 * @see HttpResponse
 * @see HttpRequest
 */
public abstract class HttpMessage {
    private static final String TAG = "HttpMessage";
    private static final int BUFFER_SIZE = 1500;
    private static final int CR = 0x0d;
    private static final int LF = 0x0a;
    private static final String CRLF = "\r\n";
    private static final String CHARSET = "utf-8";

    private InetAddress mAddress;
    private int mPort;
    private final HttpHeader mHeaders;
    private String mVersion = Http.DEFAULT_HTTP_VERSION;
    private byte[] mBodyBinary;
    private String mBody;

    /**
     * インスタンス作成
     */
    public HttpMessage() {
        mHeaders = new HttpHeader();
    }

    /**
     * 宛先アドレス情報を返す。
     *
     * @return 宛先アドレス情報。
     */
    @Nullable
    public InetAddress getAddress() {
        return mAddress;
    }

    /**
     * 宛先アドレスを登録する。
     *
     * @param address 宛先アドレス。
     */
    public void setAddress(@Nullable InetAddress address) {
        mAddress = address;
    }

    /**
     * 宛先ポート番号を返す。
     *
     * @return 宛先ポート番号
     */
    public int getPort() {
        return mPort;
    }

    /**
     * 宛先ポート番号を設定する。
     *
     * @param port 宛先ポート番号
     */
    public void setPort(int port) {
        mPort = port;
    }

    /**
     * アドレスとポート番号の組み合わせ文字列を返す。
     *
     * @return アドレスとポート番号の組み合わせ文字列
     */
    @Nonnull
    public String getAddressString() throws IllegalStateException {
        if (mAddress == null) {
            throw new IllegalStateException("address must be set");
        }
        if (mPort == 80 || mPort <= 0) {
            return mAddress.getHostAddress();
        }
        return mAddress.getHostAddress() + ":" + String.valueOf(mPort);
    }

    /**
     * 宛先SocketAddressを返す
     *
     * @return 宛先SocketAddress
     */
    @Nonnull
    public SocketAddress getSocketAddress() throws IllegalStateException {
        if (mAddress == null) {
            throw new IllegalStateException("address must be set");
        }
        return new InetSocketAddress(mAddress, mPort);
    }

    /**
     * Start Lineを返す。
     *
     * @return Start Line
     */
    @Nullable
    public abstract String getStartLine();

    /**
     * Start Lineを設定する。
     *
     * @param line Start Line
     */
    public abstract void setStartLine(@Nonnull String line) throws IllegalArgumentException;

    /**
     * HTTPバージョンの値を返す。
     *
     * @return HTTPバージョン
     */
    @Nonnull
    public String getVersion() {
        return mVersion;
    }

    /**
     * HTTPバージョンを設定する。
     *
     * @param version HTTPバージョン
     */
    public void setVersion(@Nonnull String version) {
        mVersion = version;
    }

    /**
     * ヘッダを設定する。
     *
     * @param name ヘッダ名
     * @param value 値
     */
    public void setHeader(@Nonnull String name, @Nonnull String value) {
        mHeaders.put(name, value);
    }

    /**
     * ヘッダの各行からヘッダの設定を行う
     *
     * @param line ヘッダの1行
     */
    public void setHeaderLine(@Nonnull String line) {
        final int pos = line.indexOf(':');
        if (pos < 0) {
            return;
        }
        final String name = line.substring(0, pos).trim();
        final String value = line.substring(pos + 1).trim();
        setHeader(name, value);
    }

    /**
     * ヘッダの値を返す。
     *
     * @param name ヘッダ名
     * @return ヘッダの値
     */
    @Nullable
    public String getHeader(@Nonnull String name) {
        return mHeaders.get(name);
    }

    /**
     * ヘッダの値からチャンク伝送か否かを返す。
     *
     * @return チャンク伝送の場合true
     */
    public boolean isChunked() {
        return mHeaders.containsValue(Http.TRANSFER_ENCODING, Http.CHUNKED);
    }

    /**
     * ヘッダの値からKeepAliveか否かを返す。
     *
     * HTTP/1.0の場合、Connection: keep-aliveの場合に
     * HTTP/1.1の場合、Connection: closeでない場合に
     * KeepAliveと判定する。
     *
     * @return KeepAliveの場合true
     */
    public boolean isKeepAlive() {
        if (mVersion.equals(Http.HTTP_1_0)) {
            return mHeaders.containsValue(Http.CONNECTION, Http.KEEP_ALIVE);
        }
        return !mHeaders.containsValue(Http.CONNECTION, Http.CLOSE);
    }

    /**
     * Content-Lengthの値を返す。
     *
     * 不明な場合0
     *
     * @return Content-Lengthの値
     */
    public int getContentLength() {
        final String len = mHeaders.get(Http.CONTENT_LENGTH);
        if (len != null) {
            try {
                return Integer.parseInt(len);
            } catch (final NumberFormatException e) {
                Log.w(TAG, e);
            }
        }
        return 0;
    }

    /**
     * メッセージボディを設定する。
     *
     * @param body メッセージボディ
     * @param withContentLength trueを指定すると登録されたボディの値からContent-Lengthを合わせて登録する。
     */
    public void setBody(@Nullable String body, boolean withContentLength) {
        setBody(body);
        if (withContentLength) {
            final int length = mBodyBinary == null ? 0 : mBodyBinary.length;
            setHeader(Http.CONTENT_LENGTH, String.valueOf(length));
        }
    }

    /**
     * メッセージボディを設定する。
     *
     * @param body メッセージボディ
     * @param withContentLength trueを指定すると登録されたボディの値からContent-Lengthを合わせて登録する。
     */
    public void setBodyBinary(@Nullable byte[] body, boolean withContentLength) {
        setBodyBinary(body);
        if (withContentLength) {
            final int length = mBodyBinary == null ? 0 : mBodyBinary.length;
            setHeader(Http.CONTENT_LENGTH, String.valueOf(length));
        }
    }

    /**
     * メッセージボディを設定する。
     *
     * @param body メッセージボディ
     */
    public void setBody(@Nullable String body) {
        mBody = body;
        if (body == null || body.isEmpty()) {
            mBodyBinary = null;
        } else {
            try {
                mBodyBinary = body.getBytes(CHARSET);
            } catch (final UnsupportedEncodingException e) {
                Log.w(TAG, e);
            }
        }
    }

    /**
     * メッセージボディを返す。
     *
     * @return メッセージボディ
     */
    @Nullable
    public String getBody() {
        if (mBody == null && mBodyBinary != null) {
            try {
                mBody = new String(mBodyBinary, CHARSET);
            } catch (final UnsupportedEncodingException e) {
                Log.w(TAG, e);
            }
        }
        return mBody;
    }

    /**
     * メッセージボディを設定する。
     *
     * @param body メッセージボディ
     */
    public void setBodyBinary(@Nullable byte[] body) {
        mBodyBinary = body;
        mBody = null;
    }

    /**
     * メッセージボディを返す。
     *
     * @return メッセージボディ
     */
    @Nullable
    public byte[] getBodyBinary() {
        return mBodyBinary;
    }

    @Override
    @Nonnull
    public String toString() {
        return getMessageString();
    }

    /**
     * ヘッダ部分を文字列として返す。
     *
     * @return ヘッダ文字列
     */
    @Nonnull
    public String getHeaderString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getStartLine());
        sb.append(CRLF);
        for (final HttpHeader.Entry entry : mHeaders.entrySet()) {
            sb.append(entry.getName());
            sb.append(": ");
            sb.append(entry.getValue());
            sb.append(CRLF);
        }
        sb.append(CRLF);
        return sb.toString();
    }

    /**
     * ヘッダ部分をbyte配列として返す。
     *
     * @return ヘッダバイナリ
     */
    @Nonnull
    private byte[] getHeaderBytes() {
        try {
            return getHeaderString().getBytes(CHARSET);
        } catch (final UnsupportedEncodingException e) {
            Log.w(TAG, e);
        }
        return new byte[0];
    }

    /**
     * メッセージを文字列として返す。
     *
     * @return メッセージ文字列
     */
    @Nonnull
    public String getMessageString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getStartLine());
        sb.append(CRLF);
        for (final HttpHeader.Entry entry : mHeaders.entrySet()) {
            sb.append(entry.getName());
            sb.append(": ");
            sb.append(entry.getValue());
            sb.append(CRLF);
        }
        sb.append(CRLF);
        final String body = getBody();
        if (body != null) {
            sb.append(body);
        }
        return sb.toString();
    }

    /**
     * 指定されたOutputStreamにメッセージの内容を書き出す。
     *
     * @param os 出力先
     * @throws IOException 入出力エラー
     */
    public void writeData(@Nonnull OutputStream os) throws IOException {
        os.write(getHeaderBytes());
        if (mBodyBinary != null) {
            os.write(mBodyBinary);
        }
        os.flush();
    }

    /**
     * 指定されたInputStreamからデータの読み出しを行う。
     *
     * @param is 入力元
     * @return 成功した場合true
     * @throws IOException 入出力エラー
     */
    public boolean readData(@Nonnull InputStream is) throws IOException {
        final String startLine = readLine(is);
        if (startLine == null || startLine.length() == 0) {
            return false;
        }
        try {
            setStartLine(startLine);
        } catch (final IllegalArgumentException e) {
            throw new IOException("Illegal start line:" + startLine);
        }
        while (true) {
            final String line = readLine(is);
            if (line == null) {
                return false;
            }
            if (line.isEmpty()) {
                break;
            }
            setHeaderLine(line);
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (isChunked()) {
            while (true) {
                int length = readChunkSize(is);
                if (length == 0) {
                    readLine(is);
                    break;
                }
                final byte[] buffer = new byte[BUFFER_SIZE];
                while (length > 0) {
                    int size = length > buffer.length ? buffer.length : length;
                    size = is.read(buffer, 0, size);
                    baos.write(buffer, 0, size);
                    length -= size;
                }
                readLine(is);
            }
        } else {
            int length = getContentLength();
            final byte[] buffer = new byte[BUFFER_SIZE];
            while (length > 0) {
                int size = length > buffer.length ? buffer.length : length;
                size = is.read(buffer, 0, size);
                baos.write(buffer, 0, size);
                length -= size;
            }
        }
        setBodyBinary(baos.toByteArray());
        return true;
    }

    private int readChunkSize(@Nonnull InputStream is) throws IOException {
        final String line = readLine(is);
        if (line == null || line.isEmpty()) {
            throw new IOException("Can not read chunk size!");
        }
        final String chunkSize = line.split(";", 2)[0];
        try {
            return Integer.parseInt(chunkSize, 16);
        } catch (final NumberFormatException e) {
            throw new IOException("Chunk format error!");
        }
    }

    private static String readLine(@Nonnull InputStream is) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            final int b = is.read();
            if (b < 0) {
                if (baos.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == LF) {
                break;
            }
            if (b == CR) {
                continue;
            }
            baos.write(b);
        }
        return baos.toString(CHARSET);
    }
}

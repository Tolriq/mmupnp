/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 */

package net.mm2d.upnp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;

/**
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class SsdpRequestMessage extends SsdpMessage {
    public SsdpRequestMessage() {
        super();
    }

    public SsdpRequestMessage(InterfaceAddress ifa, InetAddress ia, byte[] message)
            throws IOException {
        super(ifa, ia, message);
    }

    @Override
    protected HttpMessage newMessage() {
        return new HttpRequest();
    }

    @Override
    protected HttpRequest getMessage() {
        return (HttpRequest) super.getMessage();
    }

    public String getMethod() {
        return getMessage().getMethod();
    }

    public void setMethod(String method) {
        getMessage().setMethod(method);
    }

    public String getUri() {
        return getMessage().getUri();
    }

    public void setUri(String uri) {
        getMessage().setUri(uri);
    }
}

/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Serviceを表すクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Service {
    /**
     * DeviceDescriptionのパース時に使用するビルダー
     *
     * @see Device#loadDescription()
     */
    public static class Builder {
        private Device mDevice;
        private String mServiceType;
        private String mServiceId;
        private String mScpdUrl;
        private String mControlUrl;
        private String mEventSubUrl;

        /**
         * インスタンス作成
         */
        public Builder() {
        }

        /**
         * このServiceを保持するDeviceを登録する。
         *
         * @param device このServiceを保持するDevice
         */
        public void setDevice(@Nonnull Device device) {
            mDevice = device;
        }

        /**
         * serviceTypeを登録する。
         *
         * @param serviceType serviceTytpe
         */
        public void setServiceType(@Nonnull String serviceType) {
            mServiceType = serviceType;
        }

        /**
         * serviceIdを登録する
         *
         * @param serviceId serviceId
         */
        public void setServiceId(@Nonnull String serviceId) {
            mServiceId = serviceId;
        }

        /**
         * SCPDURLを登録する
         *
         * @param scpdUrl ScpdURL
         */
        public void setScpdUrl(@Nonnull String scpdUrl) {
            mScpdUrl = scpdUrl;
        }

        /**
         * conrolURLを登録する。
         *
         * @param controlUrl controlURL
         */
        public void setControlUrl(@Nonnull String controlUrl) {
            mControlUrl = controlUrl;
        }

        /**
         * eventSubURLを登録する。
         *
         * @param eventSubUrl eventSubURL
         */
        public void setEventSubUrl(@Nonnull String eventSubUrl) {
            mEventSubUrl = eventSubUrl;
        }

        /**
         * Serviceのインスタンスを作成する。
         *
         * @return Serviceのインスタンス
         * @throws IllegalStateException 必須パラメータが設定されていない場合
         */
        @Nonnull
        public Service build() throws IllegalStateException {
            if (mDevice == null) {
                throw new IllegalStateException("device must be set.");
            }
            if (mServiceType == null) {
                throw new IllegalStateException("serviceType must be set.");
            }
            if (mServiceId == null) {
                throw new IllegalStateException("serviceId must be set.");
            }
            if (mScpdUrl == null) {
                throw new IllegalStateException("SCDPURL must be set.");
            }
            if (mControlUrl == null) {
                throw new IllegalStateException("controlURL must be set.");
            }
            if (mEventSubUrl == null) {
                throw new IllegalStateException("eventSubURL must be set.");
            }
            return new Service(this);
        }
    }

    private static final String TAG = "Service";
    private final ControlPoint mControlPoint;
    private final Device mDevice;
    private String mDescription;
    private final String mServiceType;
    private final String mServiceId;
    private final String mScpdUrl;
    private final String mControlUrl;
    private final String mEventSubUrl;
    private List<Action> mActionList;
    private final Map<String, Action> mActionMap;
    private List<StateVariable> mStateVariableList;
    private final Map<String, StateVariable> mStateVariableMap;
    private long mSubscriptionStart;
    private long mSubscriptionTimeout;
    private String mSubscriptionId;

    private Service(Builder builder) {
        mDevice = builder.mDevice;
        mControlPoint = mDevice.getControlPoint();
        mServiceType = builder.mServiceType;
        mServiceId = builder.mServiceId;
        mScpdUrl = builder.mScpdUrl;
        mControlUrl = builder.mControlUrl;
        mEventSubUrl = builder.mEventSubUrl;
        mActionMap = new LinkedHashMap<>();
        mStateVariableMap = new LinkedHashMap<>();
    }

    /**
     * このServiceを保持するDeviceを返す。
     *
     * @return このServiceを保持するDevice
     */
    @Nonnull
    public Device getDevice() {
        return mDevice;
    }

    /**
     * URL関連プロパティの値からURLに変換する。
     *
     * @param url URLプロパティ値
     * @return URLオブジェクト
     * @throws MalformedURLException
     * @see Device#getAbsoluteUrl(String)
     */
    @Nonnull
    URL getAbsoluteUrl(String url) throws MalformedURLException {
        return mDevice.getAbsoluteUrl(url);
    }

    /**
     * serviceTypeを返す。
     *
     * @return serviceType
     */
    @Nonnull
    public String getServiceType() {
        return mServiceType;
    }

    /**
     * serviceIdを返す。
     *
     * @return serviceId
     */
    @Nonnull
    public String getServiceId() {
        return mServiceId;
    }

    /**
     * SCPDURLを返す。
     *
     * @return SCPDURL
     */
    @Nonnull
    public String getScpdUrl() {
        return mScpdUrl;
    }

    /**
     * controlURLを返す。
     *
     * @return controlURL
     */
    @Nonnull
    public String getControlUrl() {
        return mControlUrl;
    }

    /**
     * eventSubURLを返す。
     *
     * @return eventSubURL
     */
    @Nonnull
    public String getEventSubUrl() {
        return mEventSubUrl;
    }

    /**
     * ServiceDescriptionのXMLを返す。
     *
     * @return ServiceDescription
     */
    @Nullable
    public String getDescription() {
        return mDescription;
    }

    /**
     * このサービスが保持する全Actionのリストを返す。
     *
     * リストは変更不可。
     *
     * @return 全Actionのリスト
     */
    @Nonnull
    public List<Action> getActionList() {
        if (mActionList == null) {
            final List<Action> list = new ArrayList<>(mActionMap.values());
            mActionList = Collections.unmodifiableList(list);
        }
        return mActionList;
    }

    /**
     * 名前から該当するActionを探す。
     *
     * 見つからない場合はnullが返る。
     *
     * @param name Action名
     * @return 該当するAction
     */
    @Nullable
    public Action findAction(@Nonnull String name) {
        return mActionMap.get(name);
    }

    /**
     * 全StateVariableのリストを返す。
     *
     * @return 全StateVariableのリスト
     */
    @Nonnull
    public List<StateVariable> getStateVariableList() {
        if (mStateVariableList == null) {
            final List<StateVariable> list = new ArrayList<>(mStateVariableMap.values());
            mStateVariableList = Collections.unmodifiableList(list);
        }
        return mStateVariableList;
    }

    /**
     * 名前から該当するStateVariableを探す。
     *
     * 見つからない場合はnullが返る。
     *
     * @param name StateVariable名
     * @return 該当するStateVariable
     */
    @Nullable
    public StateVariable findStateVariable(@Nonnull String name) {
        return mStateVariableMap.get(name);
    }

    /**
     * SCPDURLからDescriptionを取得し、パースする。
     *
     * 可能であればKeepAliveを行う。
     *
     * @param client 通信に使用するHttpClient
     * @throws IOException 通信エラー
     * @throws SAXException XMLパースエラー
     * @throws ParserConfigurationException XMLパーサーエラー
     */
    void loadDescription(@Nonnull HttpClient client)
            throws IOException, SAXException, ParserConfigurationException {
        final URL url = getAbsoluteUrl(mScpdUrl);
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.GET);
        request.setUrl(url, true);
        request.setHeader(Http.USER_AGENT, Http.USER_AGENT_VALUE);
        request.setHeader(Http.CONNECTION, Http.KEEP_ALIVE);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            Log.i(TAG, response.toString());
            throw new IOException(response.getStartLine());
        }
        mDescription = response.getBody();
        parseDescription(mDescription);
    }

    private void parseDescription(@Nonnull String xml)
            throws IOException, SAXException, ParserConfigurationException {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        final DocumentBuilder db = dbf.newDocumentBuilder();
        final Document doc = db.parse(new InputSource(new StringReader(xml)));
        final List<Action.Builder> alist = parseActionList(doc.getElementsByTagName("action"));
        parseStateVariableList(doc.getElementsByTagName("stateVariable"));
        for (final Action.Builder builder : alist) {
            for (final Argument.Builder b : builder.getArgumentBuilderList()) {
                final String name = b.getRelatedStateVariableName();
                final StateVariable v = mStateVariableMap.get(name);
                b.setRelatedStateVariable(v);
            }
            final Action a = builder.build();
            mActionMap.put(a.getName(), a);
        }
    }

    @Nonnull
    private List<Action.Builder> parseActionList(@Nonnull NodeList nodeList) {
        final List<Action.Builder> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(parseAction((Element) nodeList.item(i)));
        }
        return list;
    }

    private void parseStateVariableList(@Nonnull NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            final StateVariable.Builder builder = parseStateVariable((Element) nodeList.item(i));
            final StateVariable variable = builder.build();
            mStateVariableMap.put(variable.getName(), variable);
        }
    }

    @Nonnull
    private Action.Builder parseAction(@Nonnull Element element) {
        final Action.Builder builder = new Action.Builder();
        builder.serService(this);
        Node node = element.getFirstChild();
        for (; node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final String tag = node.getLocalName();
            if ("name".equals(tag)) {
                builder.setName(node.getTextContent());
            } else if ("argumentList".equals(tag)) {
                for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    if ("argument".equals(c.getLocalName())) {
                        builder.addArgumentBuilder(parseArgument((Element) c));
                    }
                }
            }
        }
        return builder;
    }

    @Nonnull
    private Argument.Builder parseArgument(@Nonnull Element element) {
        final Argument.Builder builder = new Argument.Builder();
        Node node = element.getFirstChild();
        for (; node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final String tag = node.getLocalName();
            if ("name".equals(tag)) {
                builder.setName(node.getTextContent());
            } else if ("direction".equals(tag)) {
                builder.setDirection(node.getTextContent());
            } else if ("relatedStateVariable".equals(tag)) {
                final String text = node.getTextContent();
                builder.setRelatedStateVariableName(text);
            }
        }
        return builder;
    }

    @Nonnull
    private StateVariable.Builder parseStateVariable(@Nonnull Element element) {
        final StateVariable.Builder builder = new StateVariable.Builder();
        builder.setService(this);
        builder.setSendEvents(element.getAttribute("sendEvents"));
        builder.setMulticast(element.getAttribute("multicast"));
        Node node = element.getFirstChild();
        for (; node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final String tag = node.getLocalName();
            if ("name".equals(tag)) {
                builder.setName(node.getTextContent());
            } else if ("dataType".equals(tag)) {
                builder.setDataType(node.getTextContent());
            } else if ("defaultValue".equals(tag)) {
                builder.setDefaultValue(node.getTextContent());
            } else if ("allowedValueList".equals(tag)) {
                Node child = node.getFirstChild();
                for (; child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    if ("allowedValue".equals(child.getLocalName())) {
                        builder.addAllowedValue(child.getTextContent());
                    }
                }
            } else if ("allowedValueRange".equals(tag)) {
                Node child = node.getFirstChild();
                for (; child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    final String ctag = child.getLocalName();
                    if ("step".equals(ctag)) {
                        builder.setStep(child.getTextContent());
                    } else if ("minimum".equals(ctag)) {
                        builder.setMinimum(child.getTextContent());
                    } else if ("maximum".equals(ctag)) {
                        builder.setMaximum(child.getTextContent());
                    }
                }
            }
        }
        return builder;
    }

    @Nonnull
    private String getCallback() {
        final StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append("http://");
        final SsdpMessage ssdp = mDevice.getSsdpMessage();
        final InterfaceAddress ifa = ssdp.getInterfaceAddress();
        sb.append(ifa.getAddress().getHostAddress());
        sb.append(':');
        final int port = mControlPoint.getEventPort();
        sb.append(String.valueOf(port));
        sb.append('/');
        sb.append(mDevice.getUdn());
        sb.append('/');
        sb.append(mServiceId);
        sb.append('>');
        return sb.toString();
    }

    private long getTimeout(@Nonnull HttpResponse response) {
        final String timeout = response.getHeader(Http.TIMEOUT).toLowerCase();
        if (timeout.contains("infinite")) {
            return -1;
        }
        final String prefix = "second-";
        final int pos = timeout.indexOf(prefix);
        if (pos < 0) {
            return 0;
        }
        final String secondSection = timeout.substring(pos + prefix.length());
        try {
            final int second = Integer.parseInt(secondSection);
            return second * 1000L;
        } catch (final NumberFormatException e) {
            Log.w(TAG, e);
        }
        return 0;
    }

    /**
     * Subscribeの実行
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean subscribe() throws IOException {
        return subscribe(false);
    }

    /**
     * Subscribeの実行
     *
     * @param keep trueを指定すると成功後、Expire前に定期的にrenewを行う。
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean subscribe(boolean keep) throws IOException {
        if (mEventSubUrl == null) {
            return false;
        }
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.SUBSCRIBE);
        final URL url = getAbsoluteUrl(mEventSubUrl);
        request.setUrl(url, true);
        request.setHeader(Http.NT, Http.UPNP_EVENT);
        request.setHeader(Http.CALLBACK, getCallback());
        request.setHeader(Http.TIMEOUT, "Second-300");
        request.setHeader(Http.CONTENT_LENGTH, "0");
        final HttpClient client = new HttpClient(false);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            System.out.println(response.toString());
            return false;
        }
        final String sid = response.getHeader(Http.SID);
        final long timeout = getTimeout(response);
        if (sid == null || sid.isEmpty() || timeout == 0) {
            System.out.println(response.toString());
            return false;
        }
        mSubscriptionId = sid;
        mSubscriptionStart = System.currentTimeMillis();
        mSubscriptionTimeout = timeout;
        mControlPoint.registerSubscribeService(this);
        if (keep) {
            mControlPoint.addSubscribeKeeper(this);
        }
        return true;
    }

    /**
     * RenewSubscribeを実行する
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean renewSubscribe() throws IOException {
        return renewSubscribe(true);
    }

    /**
     * RenewSubscribeを実行する
     *
     * @param notify trueの場合期限が変化したことをSubscribeKeeperに通知する
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    boolean renewSubscribe(boolean notify) throws IOException {
        if (mEventSubUrl == null || mSubscriptionId == null) {
            return false;
        }
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.SUBSCRIBE);
        final URL url = getAbsoluteUrl(mEventSubUrl);
        request.setUrl(url, true);
        request.setHeader(Http.SID, mSubscriptionId);
        request.setHeader(Http.TIMEOUT, "Second-300");
        request.setHeader(Http.CONTENT_LENGTH, "0");
        final HttpClient client = new HttpClient(false);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            System.out.println(response.toString());
            return false;
        }
        final String sid = response.getHeader(Http.SID);
        final long timeout = getTimeout(response);
        if (sid == null || sid.isEmpty()
                || !sid.equals(mSubscriptionId) || timeout == 0) {
            System.out.println(response.toString());
            return false;
        }
        mSubscriptionStart = System.currentTimeMillis();
        mSubscriptionTimeout = timeout;
        if (notify) {
            mControlPoint.renewSubscribeService();
        }
        return true;
    }

    /**
     * Unsubscribeを実行する
     *
     * @return 成功時true
     * @throws IOException 通信エラー
     */
    public boolean unsubscribe() throws IOException {
        if (mEventSubUrl == null || mSubscriptionId == null) {
            return false;
        }
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.UNSUBSCRIBE);
        final URL url = getAbsoluteUrl(mEventSubUrl);
        request.setUrl(url, true);
        request.setHeader(Http.SID, mSubscriptionId);
        request.setHeader(Http.CONTENT_LENGTH, "0");
        final HttpClient client = new HttpClient(false);
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK) {
            System.out.println(response.toString());
            return false;
        }
        mControlPoint.unregisterSubscribeService(this);
        mSubscriptionId = null;
        mSubscriptionStart = 0;
        mSubscriptionTimeout = 0;
        return true;
    }

    /**
     * Subscribeの期限切れ通知
     */
    void expired() {
        mSubscriptionId = null;
        mSubscriptionStart = 0;
        mSubscriptionTimeout = 0;
    }

    /**
     * SID(SubscriptionID)を返す。
     *
     * @return SubscriptionID
     */
    @Nullable
    public String getSubscriptionId() {
        return mSubscriptionId;
    }

    /**
     * Subscriptionの開始時刻
     *
     * @return Subscriptionの開始時刻
     */
    public long getSubscriptionStart() {
        return mSubscriptionStart;
    }

    /**
     * Subscriptionの有効期間
     * 
     * @return Subscriptionの有効期間
     */
    public long getSubscriptionTimeout() {
        return mSubscriptionTimeout;
    }
}

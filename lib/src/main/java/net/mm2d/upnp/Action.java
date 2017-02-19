/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;
import net.mm2d.util.XmlUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Actionを表現するクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Action {
    private static final String TAG = Action.class.getSimpleName();

    /**
     * ServiceDescriptionのパース時に使用するビルダー
     *
     * @see DeviceParser#loadDescription(HttpClient, Device.Builder)
     * @see ServiceParser#loadDescription(HttpClient, String, Service.Builder)
     */
    public static class Builder {
        private Service mService;
        private String mName;
        @Nonnull
        private final List<Argument.Builder> mArgumentList;

        /**
         * インスタンス作成。
         */
        public Builder() {
            mArgumentList = new ArrayList<>();
        }

        /**
         * このActionを保持するServiceへの参照を登録。
         *
         * @param service このActionを保持するService
         * @return Builder
         */
        @Nonnull
        public Builder setService(@Nonnull Service service) {
            mService = service;
            return this;
        }

        /**
         * Action名を登録する。
         *
         * @param name Action名
         * @return Builder
         */
        @Nonnull
        public Builder setName(@Nonnull String name) {
            mName = name;
            return this;
        }

        /**
         * Argumentのビルダーを登録する。
         *
         * <p>Actionのインスタンス作成後にArgumentを登録することはできない
         *
         * @param argument Argumentのビルダー
         * @return Builder
         */
        @Nonnull
        public Builder addArgumentBuilder(@Nonnull Argument.Builder argument) {
            mArgumentList.add(argument);
            return this;
        }

        /**
         * Argumentのビルダーリストを返す。
         *
         * @return Argumentのビルダーリスト
         */
        @Nonnull
        public List<Argument.Builder> getArgumentBuilderList() {
            return mArgumentList;
        }

        /**
         * Actionのインスタンスを作成する。
         *
         * @return Actionのインスタンス
         * @throws IllegalStateException 必須パラメータが設定されていない場合
         */
        @Nonnull
        public Action build() throws IllegalStateException {
            if (mService == null) {
                throw new IllegalStateException("service must be set.");
            }
            if (mName == null) {
                throw new IllegalStateException("name must be set.");
            }
            return new Action(this);
        }
    }

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_STYLE = "http://schemas.xmlsoap.org/soap/encoding/";
    @Nonnull
    private final Service mService;
    @Nonnull
    private final String mName;
    @Nonnull
    private final Map<String, Argument> mArgumentMap;
    private List<Argument> mArgumentList;
    @Nonnull
    private HttpClientFactory mHttpClientFactory = new HttpClientFactory();

    private Action(@Nonnull Builder builder) {
        mService = builder.mService;
        mName = builder.mName;
        mArgumentMap = new LinkedHashMap<>(builder.mArgumentList.size());
        for (final Argument.Builder argumentBuilder : builder.mArgumentList) {
            final Argument argument = argumentBuilder.setAction(this).build();
            mArgumentMap.put(argument.getName(), argument);
        }
    }

    /**
     * このActionを保持するServiceを返す。
     *
     * @return このActionを保持するService
     */
    @Nonnull
    public Service getService() {
        return mService;
    }

    /**
     * Action名を返す。
     *
     * @return Action名
     */
    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * Argumentリストを返す。
     *
     * <p>リストは変更不可であり、
     * 変更しようとするとUnsupportedOperationExceptionが発生する。
     *
     * @return Argumentリスト
     */
    @Nonnull
    public List<Argument> getArgumentList() {
        if (mArgumentList == null) {
            mArgumentList = Collections.unmodifiableList(new ArrayList<>(mArgumentMap.values()));
        }
        return mArgumentList;
    }

    /**
     * 指定名に合致するArgumentを返す。
     *
     * @param name Argument名
     * @return Argument
     */
    @Nullable
    public Argument findArgument(@Nonnull String name) {
        return mArgumentMap.get(name);
    }

    @Nonnull
    private String getSoapActionName() {
        return '"' + mService.getServiceType() + '#' + mName + '"';
    }

    @Nonnull
    private String getResponseTagName() {
        return mName + "Response";
    }

    /**
     * HttpClientのファクトリークラスを変更する。
     *
     * @param factory ファクトリークラス
     */
    void setHttpClientFactory(@Nonnull HttpClientFactory factory) {
        mHttpClientFactory = factory;
    }

    @Nonnull
    private HttpClient createHttpClient() {
        return mHttpClientFactory.createHttpClient(false);
    }

    /**
     * Actionを実行する。
     *
     * <p>実行引数及び実行結果はArgument名をkeyとし、値をvalueとしたMapでやり取りする。
     * 値はすべてStringで表現する。
     * Argument(StateVariable)のDataTypeに応じた値チェックは行われない。
     * 引数に不足があった場合、StateVariableにデフォルト値が定義されている場合に限り、その値が反映される。
     * デフォルト値が定義されていない場合は、DataTypeに違反していても空として扱う。
     * 実行後エラー応答があった場合のパースには未対応であり、IOExceptionが発生するのみ。
     *
     * @param arguments 引数
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     */
    @Nonnull
    public Map<String, String> invoke(@Nonnull Map<String, String> arguments)
            throws IOException {
        final String soap = makeSoap(arguments);
        final URL url = mService.getAbsoluteUrl(mService.getControlUrl());
        final HttpRequest request = makeHttpRequest(url, soap);
        final HttpClient client = createHttpClient();
        final HttpResponse response = client.post(request);
        if (response.getStatus() != Http.Status.HTTP_OK || response.getBody() == null) {
            Log.w(TAG, response.toString());
            throw new IOException(response.getStartLine());
        }
        try {
            return parseResponse(response.getBody());
        } catch (final SAXException | ParserConfigurationException e) {
            throw new IOException(response.getBody(), e);
        }
    }

    /**
     * SOAP送信のためのHttpRequestを作成する。
     *
     * @param url  接続先URL
     * @param soap SOAPの文字列
     * @return SOAP送信用HttpRequest
     * @throws IOException 通信で問題が発生した場合
     */
    @Nonnull
    private HttpRequest makeHttpRequest(@Nonnull URL url, @Nonnull String soap) throws IOException {
        final HttpRequest request = new HttpRequest();
        request.setMethod(Http.POST);
        request.setUrl(url, true);
        request.setHeader(Http.SOAPACTION, getSoapActionName());
        request.setHeader(Http.USER_AGENT, Property.USER_AGENT_VALUE);
        request.setHeader(Http.CONNECTION, Http.CLOSE);
        request.setHeader(Http.CONTENT_TYPE, Http.CONTENT_TYPE_DEFAULT);
        request.setBody(soap, true);
        return request;
    }

    /**
     * SOAP ActionのXML文字列を作成する。
     *
     * @param arguments 引数の入力
     * @return SOAP ActionのXML文字列
     * @throws IOException 通信で問題が発生した場合
     */
    @Nonnull
    private String makeSoap(@Nonnull Map<String, String> arguments) throws IOException {
        try {
            final Document document = XmlUtils.newDocument(true);
            final Element action = makeUpToActionElement(document);
            setArgument(document, action, arguments);
            return formatXmlString(document);
        } catch (DOMException
                | TransformerFactoryConfigurationError
                | TransformerException
                | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    /**
     * SOAP ActionのXMLのActionElementまでを作成する
     *
     * @param document XML Document
     * @return ActionElement
     */
    @Nonnull
    private Element makeUpToActionElement(@Nonnull Document document) {
        final Element envelope = document.createElementNS(SOAP_NS, "s:Envelope");
        document.appendChild(envelope);
        final Attr style = document.createAttributeNS(SOAP_NS, "s:encodingStyle");
        style.setNodeValue(SOAP_STYLE);
        envelope.setAttributeNode(style);
        final Element body = document.createElementNS(SOAP_NS, "s:Body");
        envelope.appendChild(body);
        final Element action = document.createElementNS(mService.getServiceType(), "u:" + mName);
        body.appendChild(action);
        return action;
    }

    /**
     * Actionの引数をXMLへ組み込む
     *
     * @param document XML Document
     * @param action   actionのElement
     * @param input    引数としての入力
     */
    private void setArgument(@Nonnull Document document, @Nonnull Element action, @Nonnull Map<String, String> input) {
        for (final Entry<String, Argument> entry : mArgumentMap.entrySet()) {
            final Argument argument = entry.getValue();
            if (!argument.isInputDirection()) {
                continue;
            }
            final Element param = document.createElement(argument.getName());
            final String value = selectArgumentValue(argument, input);
            if (value != null) {
                param.setTextContent(value);
            }
            action.appendChild(param);
        }
    }

    /**
     * Argumentの値を選択する。
     *
     * <p>入力に値があればそれを採用し、なければデフォルト値を採用する。
     * どちらもなければnullが返る。
     *
     * @param argument Argument
     * @param input    引数としての入力
     * @return 選択されたArgumentの値
     */
    @Nullable
    private static String selectArgumentValue(@Nonnull Argument argument, @Nonnull Map<String, String> input) {
        final String value = input.get(argument.getName());
        if (value != null) {
            return value;
        }
        return argument.getRelatedStateVariable().getDefaultValue();
    }

    /**
     * XML Documentを文字列に変換する
     *
     * @param document 変換するXML Document
     * @return 変換された文字列
     * @throws TransformerException 変換処理に問題が発生した場合
     */
    @Nonnull
    private static String formatXmlString(@Nonnull Document document) throws TransformerException {
        final TransformerFactory tf = TransformerFactory.newInstance();
        final Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        final StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(sw));
        return sw.toString();
    }

    /**
     * Actionに対する応答をパースする
     *
     * @param xml 応答となるXML
     * @return Actionに対する応答であるXML文字列
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Map<String, String> parseResponse(@Nonnull String xml)
            throws ParserConfigurationException, IOException, SAXException {
        final Map<String, String> result = new HashMap<>();
        Node node = findResponseElement(xml).getFirstChild();
        for (; node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final String tag = node.getLocalName();
            final String text = node.getTextContent();
            if (findArgument(tag) == null) {
                // Optionalな情報としてArgumentに記述されていないタグが含まれる可能性があるためログ出力に留める
                Log.d(TAG, "invalid argument:" + tag + "->" + text);
            }
            result.put(tag, text);
        }
        return result;
    }

    /**
     * Actionに対する応答をパースし、ResponseタグのElementを探し出す。
     *
     * @param xml Actionに対する応答であるXML文字列
     * @return ResponseタグのElement
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Element findResponseElement(@Nonnull String xml)
            throws ParserConfigurationException, SAXException, IOException {
        final String responseTag = getResponseTagName();
        final Document doc = XmlUtils.newDocument(true, xml);
        final Element envelope = doc.getDocumentElement();
        final Element body = XmlUtils.findChildElementByLocalName(envelope, "Body");
        if (body == null) {
            throw new IOException("no body tag");
        }
        final Element response = XmlUtils.findChildElementByLocalName(body, responseTag);
        if (response == null) {
            throw new IOException("no response tag");
        }
        return response;
    }
}
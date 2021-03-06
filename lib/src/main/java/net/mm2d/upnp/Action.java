/*
 * Copyright (c) 2016 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.log.Log;
import net.mm2d.upnp.Http.Status;
import net.mm2d.util.StringPair;
import net.mm2d.util.TextUtils;
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
 * @author <a href="mailto:ryo@mm2d.net">大前良介 (OHMAE Ryosuke)</a>
 */
public class Action {
    /**
     * ServiceDescriptionのパース時に使用するビルダー
     *
     * @see DeviceParser#loadDescription(HttpClient, Device.Builder)
     * @see ServiceParser#loadDescription(HttpClient, Device.Builder, Service.Builder)
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
        public Builder setService(@Nonnull final Service service) {
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
        public Builder setName(@Nonnull final String name) {
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
        public Builder addArgumentBuilder(@Nonnull final Argument.Builder argument) {
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

    /**
     * エラーレスポンスの faultcode を格納するkey。
     *
     * <p>正常な応答であれば、SOAPのnamespaceがついた"Client"が格納されている。
     */
    public static final String FAULT_CODE_KEY = "faultcode";
    /**
     * エラーレスポンスの faultstring を格納するkey。
     *
     * <p>正常な応答であれば、"UPnPError"が格納されている。
     */
    public static final String FAULT_STRING_KEY = "faultstring";
    /**
     * エラーレスポンスの detail/UPnPError/errorCode を格納するkey。
     */
    public static final String ERROR_CODE_KEY = "UPnPError/errorCode";
    /**
     * エラーレスポンスの detail/UPnPError/errorDescription を格納するkey.
     */
    public static final String ERROR_DESCRIPTION_KEY = "UPnPError/errorDescription";
    private static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
    private static final String XMLNS_PREFIX = "xmlns:";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_STYLE = "http://schemas.xmlsoap.org/soap/encoding/";
    @Nonnull
    private final Service mService;
    @Nonnull
    private final String mName;
    @Nonnull
    private final Map<String, Argument> mArgumentMap;
    @Nullable
    private List<Argument> mArgumentList;

    private Action(@Nonnull final Builder builder) {
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
    public Argument findArgument(@Nonnull final String name) {
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

    // VisibleForTesting
    @Nonnull
    HttpClient createHttpClient() {
        return new HttpClient(false);
    }

    /**
     * Actionを実行する。
     *
     * <p>実行後エラー応答を受け取った場合は、IOExceptionを発生させる。
     * エラー応答の内容を取得する必要がある場合は{@link #invoke(Map, boolean)}を使用し、第二引数にtrueを指定する。
     * このメソッドは{@link #invoke(Map, boolean)}の第二引数にfalseを指定した場合と等価である。
     *
     * <p>実行引数及び実行結果は引数名をkeyとし、値をvalueとしたMapで表現する。
     * 値はすべてStringで表現する。
     * Argument(StateVariable)のDataTypeやAllowedValueに応じた値チェックは行われない。
     *
     * <p>引数として渡したMapの中にArgumentに記載のない値を設定していても無視される。
     *
     * <p>引数に不足があった場合、StateVariableにデフォルト値が定義されている場合に限り、その値が設定される。
     * デフォルト値が定義されていない場合は、DataTypeに違反していても空として扱う。
     *
     * <p>実行結果にArgumentに記載のない値が入っていた場合は無視することはなく、
     * Argumentに記載のあったものと同様にkey/valueの形で戻り値のMapに設定される。
     *
     * @param argumentValues 引数への入力値
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     * @see #invoke(Map, boolean)
     */
    @Nonnull
    public Map<String, String> invoke(@Nonnull final Map<String, String> argumentValues)
            throws IOException {
        return invoke(argumentValues, false);
    }

    /**
     * Actionを実行する。
     *
     * <p>実行引数及び実行結果は引数名をkeyとし、値をvalueとしたMapで表現する。
     * 値はすべてStringで表現する。
     * Argument(StateVariable)のDataTypeやAllowedValueに応じた値チェックは行われない。
     *
     * <p>引数として渡したMapの中にArgumentに記載のない値を設定していても無視される。
     *
     * <p>引数に不足があった場合、StateVariableにデフォルト値が定義されている場合に限り、その値が設定される。
     * デフォルト値が定義されていない場合は、DataTypeに違反していても空として扱う。
     *
     * <p>実行結果にArgumentに記載のない値が入っていた場合は無視することはなく、
     * Argumentに記載のあったものと同様にkey/valueの形で戻り値のMapに設定される。
     *
     * <p>第二引数がfalseの場合、エラーレスポンスが返却された場合は、IOExceptionを発生させる。
     * trueを指定すると、エラーレスポンスもパースして戻り値として返却する。
     * この場合、戻り値のMapのkeyとして
     * エラーレスポンスが仕様に従うなら'faultcode','faultstring','UPnPError/errorCode',が含まれ
     * 'UPnPError/errorDescription'も含まれている場合がある。
     * このメソッドでは'UPnPError/errorCode'が含まれていない場合は、
     * エラーレスポンスの異常として、IOExceptionを発生させる。
     *
     * @param argumentValues      引数への入力値
     * @param returnErrorResponse エラーレスポンス受信時の処理を指定、trueにするとエラーもパースして戻り値で返す。falseにするとIOExceptionを発生させる。
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     * @see #FAULT_CODE_KEY
     * @see #FAULT_STRING_KEY
     * @see #ERROR_CODE_KEY
     * @see #ERROR_DESCRIPTION_KEY
     */
    @Nonnull
    public Map<String, String> invoke(
            @Nonnull final Map<String, String> argumentValues,
            final boolean returnErrorResponse)
            throws IOException {
        final List<StringPair> arguments = makeArguments(argumentValues);
        final String soap = makeSoap(null, arguments);
        final Map<String, String> result = invokeInner(soap);
        if (!returnErrorResponse && result.containsKey(ERROR_CODE_KEY)) {
            throw new IOException("");
        }
        return result;
    }

    /**
     * Actionを実行する。【試験的実装】
     *
     * <p>※試験的実装であり、将来的に変更、削除される可能性が高い
     *
     * <p>実行後エラー応答を受け取った場合は、IOExceptionを発生させる。
     * エラー応答の内容を取得する必要がある場合は{@link #invoke(Map, Map, Map, boolean)}を使用し、第四引数にtrueを指定する。
     * このメソッドは{@link #invoke(Map, Map, Map, boolean)}の第四引数にfalseを指定した場合と等価である。
     *
     * <p>実行引数及び実行結果は引数名をkeyとし、値をvalueとしたMapで表現する。
     * 値はすべてStringで表現する。
     * Argument(StateVariable)のDataTypeやAllowedValueに応じた値チェックは行われない。
     *
     * <p>第一引数として渡したMapの中にArgumentに記載のない値を設定していても無視される。
     *
     * <p>引数に不足があった場合、StateVariableにデフォルト値が定義されている場合に限り、その値が設定される。
     * デフォルト値が定義されていない場合は、DataTypeに違反していても空として扱う。
     *
     * <p>第二引数として第三引数で使用するNamespaceを指定する。不要であればnullを指定する。
     * StringPairのリストであり、keyとしてprefixを、valueとしてURIを指定する。
     * key/valueともにnullを指定することはできない。
     * この引数によって与えたNamespaceはAction Elementに追加される。
     *
     * <p>第三引数として渡したStringPairのリストは純粋にSOAP XMLのAction Elementの子要素として追加される。
     * keyとして引数名、valueとして値を指定する。keyはnullであってはならない。valueがnullの場合は空の引数指定となる。
     * この際Argumentの値との関係性はチェックされずすべてがそのまま追加される。
     * ただし、Namespaceとして登録されないprefixを持っているなどXMLとして不正な引数を与えると失敗する。
     *
     * <p>実行結果にArgumentに記載のない値が入っていた場合は無視することはなく、
     * Argumentに記載のあったものと同様にkey/valueの形で戻り値のMapに設定される。
     *
     * @param argumentValues  引数への入力値
     * @param customNamespace カスタム引数のNamespace情報、不要な場合null
     * @param customArguments カスタム引数
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     * @see #invoke(Map, Map, Map, boolean)
     */
    @Nonnull
    public Map<String, String> invoke(
            @Nonnull final Map<String, String> argumentValues,
            @Nullable final Map<String, String> customNamespace,
            @Nonnull final Map<String, String> customArguments)
            throws IOException {
        return invoke(argumentValues, customNamespace, customArguments, false);
    }

    /**
     * Actionを実行する。【試験的実装】
     *
     * <p>※試験的実装であり、将来的に変更、削除される可能性が高い
     *
     * <p>実行引数及び実行結果は引数名をkeyとし、値をvalueとしたMapで表現する。
     * 値はすべてStringで表現する。
     * Argument(StateVariable)のDataTypeやAllowedValueに応じた値チェックは行われない。
     *
     * <p>第一引数として渡したMapの中にArgumentに記載のない値を設定していても無視される。
     *
     * <p>引数に不足があった場合、StateVariableにデフォルト値が定義されている場合に限り、その値が設定される。
     * デフォルト値が定義されていない場合は、DataTypeに違反していても空として扱う。
     *
     * <p>第二引数として第三引数で使用するNamespaceを指定する。不要であればnullを指定する。
     * StringPairのリストであり、keyとしてprefixを、valueとしてURIを指定する。
     * key/valueともにnullを指定することはできない。
     * この引数によって与えたNamespaceはAction Elementに追加される。
     *
     * <p>第三引数として渡したStringPairのリストは純粋にSOAP XMLのAction Elementの子要素として追加される。
     * keyとして引数名、valueとして値を指定する。keyはnullであってはならない。valueがnullの場合は空の引数指定となる。
     * この際Argumentの値との関係性はチェックされずすべてがそのまま追加される。
     * ただし、Namespaceとして登録されないprefixを持っているなどXMLとして不正な引数を与えると失敗する。
     *
     * <p>実行結果にArgumentに記載のない値が入っていた場合は無視することはなく、
     * Argumentに記載のあったものと同様にkey/valueの形で戻り値のMapに設定される。
     *
     * <p>第四引数がfalseの場合、エラーレスポンスが返却された場合は、IOExceptionを発生させる。
     * trueを指定すると、エラーレスポンスもパースして戻り値として返却する。
     * この場合、戻り値のMapのkeyとして
     * エラーレスポンスが仕様に従うなら'faultcode','faultstring','UPnPError/errorCode',が含まれ
     * 'UPnPError/errorDescription'も含まれている場合がある。
     * このメソッドでは'UPnPError/errorCode'が含まれていない場合は、
     * エラーレスポンスの異常として、IOExceptionを発生させる。
     *
     * @param argumentValues      引数への入力値
     * @param customNamespace     カスタム引数のNamespace情報、不要な場合null
     * @param customArguments     カスタム引数
     * @param returnErrorResponse エラーレスポンス受信時の処理を指定、trueにするとエラーもパースして戻り値で返す。falseにするとIOExceptionを発生させる。
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     * @see #FAULT_CODE_KEY
     * @see #FAULT_STRING_KEY
     * @see #ERROR_CODE_KEY
     * @see #ERROR_DESCRIPTION_KEY
     */
    @Nonnull
    public Map<String, String> invoke(
            @Nonnull final Map<String, String> argumentValues,
            @Nullable final Map<String, String> customNamespace,
            @Nonnull final Map<String, String> customArguments,
            final boolean returnErrorResponse)
            throws IOException {
        final List<StringPair> arguments = makeArguments(argumentValues);
        appendArgument(arguments, customArguments);
        final String soap = makeSoap(customNamespace, arguments);
        final Map<String, String> result = invokeInner(soap);
        if (!returnErrorResponse && result.containsKey(ERROR_CODE_KEY)) {
            throw new IOException("");
        }
        return result;
    }

    /**
     * 入力をもとに引数リストを作成する。
     *
     * @param argumentValues 引数への入力値
     * @return 引数リスト
     */
    @Nonnull
    private List<StringPair> makeArguments(@Nonnull final Map<String, String> argumentValues) {
        final List<StringPair> list = new ArrayList<>();
        for (final Entry<String, Argument> entry : mArgumentMap.entrySet()) {
            final Argument argument = entry.getValue();
            if (!argument.isInputDirection()) {
                continue;
            }
            list.add(new StringPair(argument.getName(), selectArgumentValue(argument, argumentValues)));
        }
        return list;
    }

    /**
     * StringPairのリストに変換した引数にカスタム引数を追加する。
     *
     * @param base      引数の追加先
     * @param arguments 追加するカスタム引数
     */
    private void appendArgument(
            @Nonnull final List<StringPair> base,
            @Nonnull final Map<String, String> arguments) {
        for (final Entry<String, String> entry : arguments.entrySet()) {
            base.add(new StringPair(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Argumentの値を選択する。
     *
     * <p>入力に値があればそれを採用し、なければデフォルト値を採用する。
     * どちらもなければnullが返る。
     *
     * @param argument       Argument
     * @param argumentValues 引数への入力値
     * @return 選択されたArgumentの値
     */
    @Nullable
    private static String selectArgumentValue(
            @Nonnull final Argument argument,
            @Nonnull final Map<String, String> argumentValues) {
        final String value = argumentValues.get(argument.getName());
        if (value != null) {
            return value;
        }
        return argument.getRelatedStateVariable().getDefaultValue();
    }

    /**
     * Actionの実行を行う。
     *
     * @param soap 送信するSOAP XML文字列
     * @return 実行結果
     * @throws IOException 実行時の何らかの通信例外及びエラー応答があった場合
     */
    @Nonnull
    private Map<String, String> invokeInner(@Nonnull final String soap)
            throws IOException {
        final URL url = mService.getAbsoluteUrl(mService.getControlUrl());
        final HttpRequest request = makeHttpRequest(url, soap);
        final HttpClient client = createHttpClient();
        final HttpResponse response = client.post(request);
        final String body = response.getBody();
        if (response.getStatus() == Status.HTTP_INTERNAL_ERROR && !TextUtils.isEmpty(body)) {
            try {
                return parseErrorResponse(body);
            } catch (final SAXException | ParserConfigurationException e) {
                throw new IOException(body, e);
            }
        }
        if (response.getStatus() != Http.Status.HTTP_OK || TextUtils.isEmpty(body)) {
            Log.w(response.toString());
            throw new IOException(response.getStartLine());
        }
        try {
            return parseResponse(body);
        } catch (final SAXException | ParserConfigurationException e) {
            throw new IOException(body, e);
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
    private HttpRequest makeHttpRequest(
            @Nonnull final URL url,
            @Nonnull final String soap)
            throws IOException {
        return new HttpRequest()
                .setMethod(Http.POST)
                .setUrl(url, true)
                .setHeader(Http.SOAPACTION, getSoapActionName())
                .setHeader(Http.USER_AGENT, Property.USER_AGENT_VALUE)
                .setHeader(Http.CONNECTION, Http.CLOSE)
                .setHeader(Http.CONTENT_TYPE, Http.CONTENT_TYPE_DEFAULT)
                .setBody(soap, true);
    }

    /**
     * SOAP ActionのXML文字列を作成する。
     *
     * @param arguments 引数
     * @return SOAP ActionのXML文字列
     * @throws IOException 通信で問題が発生した場合
     */
    // VisibleForTesting
    @Nonnull
    String makeSoap(
            @Nullable final Map<String, String> namespaces,
            @Nonnull final List<StringPair> arguments)
            throws IOException {
        try {
            final Document document = XmlUtils.newDocument(true);
            final Element action = makeUpToActionElement(document);
            setNamespace(action, namespaces);
            setArgument(document, action, arguments);
            return formatXmlString(document);
        } catch (DOMException
                | TransformerFactoryConfigurationError
                | TransformerException
                | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    private static void setNamespace(
            @Nonnull final Element action,
            @Nullable final Map<String, String> namespace) {
        if (namespace == null) {
            return;
        }
        for (final Entry<String, String> entry : namespace.entrySet()) {
            action.setAttributeNS(XMLNS_URI, XMLNS_PREFIX + entry.getKey(), entry.getValue());
        }
    }

    /**
     * SOAP ActionのXMLのActionElementまでを作成する
     *
     * @param document XML Document
     * @return ActionElement
     */
    @Nonnull
    private Element makeUpToActionElement(@Nonnull final Document document) {
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
     * @param document  XML Document
     * @param action    actionのElement
     * @param arguments 引数
     */
    private static void setArgument(
            @Nonnull final Document document,
            @Nonnull final Element action,
            @Nonnull final List<StringPair> arguments) {
        for (final StringPair pair : arguments) {
            final Element param = document.createElement(pair.getKey());
            final String value = pair.getValue();
            if (value != null) {
                param.setTextContent(value);
            }
            action.appendChild(param);
        }
    }

    /**
     * XML Documentを文字列に変換する
     *
     * @param document 変換するXML Document
     * @return 変換された文字列
     * @throws TransformerException 変換処理に問題が発生した場合
     */
    // VisibleForTesting
    @Nonnull
    String formatXmlString(@Nonnull final Document document)
            throws TransformerException {
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
     * @return Actionに対する応答、argument名をkeyとする。
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Map<String, String> parseResponse(@Nonnull final String xml)
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
                Log.d("invalid argument:" + tag + "->" + text);
            }
            result.put(tag, text);
        }
        return result;
    }

    /**
     * Actionに対する応答をパースし、ResponseタグのElementを探して返す。
     *
     * @param xml Actionに対する応答であるXML文字列
     * @return ResponseタグのElement
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Element findResponseElement(@Nonnull final String xml)
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

    /**
     * Actionに対するエラー応答をパースする
     *
     * @param xml 応答となるXML
     * @return エラー応答の情報、'faultcode','faultstring','UPnPError/errorCode','UPnPError/errorDescription'
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Map<String, String> parseErrorResponse(@Nonnull final String xml)
            throws ParserConfigurationException, IOException, SAXException {
        final Map<String, String> result = new HashMap<>();
        Node node = findFaultElement(xml).getFirstChild();
        for (; node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final String tag = node.getLocalName();
            if (tag.equals("detail")) {
                result.putAll(parseErrorDetail(node));
                continue;
            }
            result.put(tag, node.getTextContent());
        }
        if (!result.containsKey(ERROR_CODE_KEY)) {
            throw new IOException("no UPnPError/errorCode tag");
        }
        return result;
    }

    /**
     * エラー応答のdetailタグ以下をパースする。
     *
     * @param detailNode detailノード
     * @return パース結果
     * @throws IOException 入力値に問題がある場合
     */
    @Nonnull
    private Map<String, String> parseErrorDetail(@Nonnull final Node detailNode) throws IOException {
        final Map<String, String> result = new HashMap<>();
        final Element error = XmlUtils.findChildElementByLocalName(detailNode, "UPnPError");
        if (error == null) {
            throw new IOException("no UPnPError tag");
        }
        for (Node node = error.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            result.put("UPnPError/" + node.getLocalName(), node.getTextContent());
        }
        return result;
    }

    /**
     * Actionに対するエラー応答をパースし、FaultタグのElementを探して返す。
     *
     * @param xml Actionに対する応答であるXML文字列
     * @return FaultタグのElement
     * @throws ParserConfigurationException XMLパーサのインスタンス化に問題がある場合
     * @throws SAXException                 XMLパース処理に問題がある場合
     * @throws IOException                  入力値に問題がある場合
     */
    @Nonnull
    private Element findFaultElement(@Nonnull final String xml)
            throws ParserConfigurationException, SAXException, IOException {
        final Document doc = XmlUtils.newDocument(true, xml);
        final Element envelope = doc.getDocumentElement();
        final Element body = XmlUtils.findChildElementByLocalName(envelope, "Body");
        if (body == null) {
            throw new IOException("no body tag");
        }
        final Element fault = XmlUtils.findChildElementByLocalName(body, "Fault");
        if (fault == null) {
            throw new IOException("no fault tag");
        }
        return fault;
    }
}

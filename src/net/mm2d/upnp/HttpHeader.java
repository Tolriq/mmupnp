/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * HTTPヘッダを表現するクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class HttpHeader {
    /**
     * ヘッダのエントリー情報
     */
    public static class Entry {
        private String mName;
        private String mValue;

        /**
         * インスタンス作成
         *
         * @param name ヘッダ名
         * @param value 値
         */
        public Entry(@Nonnull String name, @Nonnull String value) {
            mName = name;
            mValue = value;
        }

        /**
         * ヘッダ名を設定する。
         *
         * @param name ヘッダ名
         */
        public void setName(@Nonnull String name) {
            mName = name;
        }

        /**
         * ヘッダ名を取得する。
         *
         * @return ヘッダ名
         */
        @Nonnull
        public String getName() {
            return mName;
        }

        /**
         * 値を設定する。
         *
         * @param value 値
         */
        public void setValue(@Nonnull String value) {
            mValue = value;
        }

        /**
         * 値を返す。
         *
         * @return 値
         */
        @Nonnull
        public String getValue() {
            return mValue;
        }
    }

    /**
     * ヘッダエントリーへSetインターフェースでアクセスさせるためのクラス。
     */
    private class EntrySet extends AbstractSet<Entry> {
        @Override
        public Iterator<Entry> iterator() {
            return mList.iterator();
        }

        @Override
        public int size() {
            return mList.size();
        }
    }

    private EntrySet mEntrySet;
    private final List<Entry> mList = new LinkedList<>();

    /**
     * ヘッダエントリー数を返す。
     *
     * @return ヘッダエントリー数
     */
    public int size() {
        return mList.size();
    }

    /**
     * ヘッダ情報が空か否かを返す。
     *
     * @return ヘッダが空のときtrue
     */
    public boolean isEmpty() {
        return mList.isEmpty();
    }

    /**
     * 指定されたヘッダ名の値を返す。
     *
     * ヘッダの検索において大文字小文字の区別は行わない。
     *
     * @param name ヘッダ名
     * @return ヘッダの値
     */
    @Nullable
    public String get(@Nonnull String name) {
        for (final Entry entry : mList) {
            if (entry.getName().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * ヘッダの削除を行う。
     *
     * ヘッダの検索において大文字小文字の区別は行わない。
     *
     * @param name ヘッダ名
     * @return 削除されたヘッダがあった場合、ヘッダの値、なかった場合null
     */
    @Nullable
    public String remove(@Nonnull String name) {
        final Iterator<Entry> i = mList.iterator();
        while (i.hasNext()) {
            final Entry entry = i.next();
            if (entry.getName().equalsIgnoreCase(name)) {
                i.remove();
                return entry.mValue;
            }
        }
        return null;
    }

    /**
     * ヘッダ情報を登録する。
     *
     * ヘッダ名は登録においては大文字小文字の区別を保持して登録される。
     * 既に同一名のヘッダが登録されている場合置換される。
     * ヘッダの重複は大文字小文字の区別を行わない。
     * 置換された場合、ヘッダ名も引数のもので置き換えられる。
     *
     * @param name ヘッダ名
     * @param value ヘッダの値
     * @return 重複があった場合、既に登録されていた値。
     */
    @Nullable
    public String put(@Nonnull String name, @Nonnull String value) {
        for (final Entry entry : mList) {
            if (entry.getName().equalsIgnoreCase(name)) {
                final String old = entry.getValue();
                entry.setName(name);
                entry.setValue(value);
                return old;
            }
        }
        mList.add(new Entry(name, value));
        return null;
    }

    /**
     * 指定ヘッダに指定文字列が含まれるかを大文字小文字の区別なく判定する。
     *
     * 該当ヘッダ名の検索も大文字小文字の区別を行わない。
     *
     * @param name ヘッダ名
     * @param value 含まれるか
     * @return 指定ヘッダにvalueが含まれる場合true
     */
    public boolean containsValue(@Nonnull String name, @Nonnull String value) {
        final String v = get(name);
        return v != null && v.toLowerCase().contains(value.toLowerCase());
    }

    /**
     * 登録所法のクリアを行う。
     */
    public void clear() {
        mList.clear();
    }

    /**
     * 登録されているヘッダ情報へのSetビューを返す。
     * 
     * @return 登録されているヘッダ情報へのSetビュー
     */
    @Nonnull
    public Set<Entry> entrySet() {
        if (mEntrySet == null) {
            mEntrySet = new EntrySet();
        }
        return mEntrySet;
    }

    @Override
    @Nonnull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry entry : mList) {
            sb.append(entry.getName());
            sb.append(": ");
            sb.append(entry.getValue());
            sb.append("\r\n");
        }
        return sb.toString();
    }
}

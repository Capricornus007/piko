/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package com.twitter.ui.widget;

import android.content.Context;
import android.preference.PreferenceCategory;

/**
 * 12.46.1-prod 起宿主移除了此類（舊 androidx.preference 設定棧整體拆除，
 * androguard 實查全 dex 0 命中）。piko 設定 UI 仍基於 android.preference
 * （OS 自帶，不受影響），故此平凡子類改由擴展自帶打包，不再依賴宿主。
 */
public class LegacyTwitterPreferenceCategory extends PreferenceCategory {

    public LegacyTwitterPreferenceCategory(Context context) {
        super(context);
    }
}

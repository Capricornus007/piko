/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * 12.46.1-prod 起設定根頁改為 Kotlin 代碼構建（com.x.settings.j.c() 組裝
 * List&lt;com.x.settings.common.s&gt;，舊 res/xml/settings_root.xml 注入點已消失）。
 *
 * SettingsEntryPatch 在 j.c() return 前調用 {@link #addRootEntry}，於返回列表
 * 追加「Piko」條目；點擊經 {@link ActivityHook#startSettingsActivity()} 開啟
 * piko 設定（與 AuthorizeAppActivity 既有路由一致）。
 *
 * com.x.settings.common.r / s、com.x.models.bd、com.x.icons.b 僅透過 stub
 * 模組編譯（compileOnly），運行時由 host APK 提供同名類。
 */
@SuppressWarnings("unused")
public final class PikoSettingsEntry implements kotlin.jvm.functions.Function0 {

    /**
     * r 的 Kotlin 預設參數遮罩。位元 32 必須為 0（onClick 非預設值，才會真正傳入），
     * 其餘對應 desc/value/opensTray/navigationTarget/dialog 全走預設（null/false）。
     * 對照 j.c() 既有條目的 442（其 onClick 為 null、navTarget 非預設）。
     */
    private static final int R_DEFAULT_MASK = 474; // 2 | 8 | 16 | 64 | 128 | 256

    @Override
    public Object invoke() {
        ActivityHook.startSettingsActivity();
        return null;
    }

    /**
     * 複製 j.c() 返回的 List（CollectionsKt.listOf 產物，不可變）並追加 Piko 區段。
     */
    public static List addRootEntry(List list) {
        com.x.models.bd title = new com.x.models.bd("Piko");
        int iconRes = ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_settings_stroke");
        com.x.icons.b icon = new com.x.icons.b(iconRes);

        com.x.settings.common.r entry = new com.x.settings.common.r(
                title, null, icon, null, false, new PikoSettingsEntry(), null, null, R_DEFAULT_MASK);

        com.x.settings.common.s section = new com.x.settings.common.s(
                null, null, Collections.singletonList(entry), 3);

        List result = new ArrayList(list);
        result.add(section);
        return result;
    }
}

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object ApplicationFingerprint : Fingerprint(
    superclass = "Lcom/twitter/app/TwitterApplication;",
    name = "attachBaseContext",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Landroid/content/Context;"),
            returnType = "V"
        )
    ),
)

// 2026-11-26：SettingsFragmentFingerprint / SettingsPreferenceFingerprint /
// AuthorizeAppActivity(Fingerprint/Virtual) / UrlInterpreterActivity(Fingerprint/Virtual)
// 已移除——對應宿主類在 12.46.1-prod 全部消失（單一 Activity 架構 +
// 舊 preference 棧拆除，androguard 實查全 dex 0 命中）。
// 設定宿主改為擴展自帶 PikoSettingsActivity；piko 路由 deeplink 改掛：
internal object PikoDeeplinkFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/settings/SettingsStatus;",
    name = "load",
)

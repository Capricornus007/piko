/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.misc.crashLogs.crashLogsPatch
import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.utils.Constants.DEEPLINK_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch

val settingsPatch =
    patch(
        description = "Adds settings",
    ) {
        dependsOn(
            checkCompatibilityPatch,
            sharedExtensionPatch,
            settingsResourcePatch,
            // redirectBMTab 依賴移除（2026-11-26）：material tabs 套件已從
            // 12.46.1-prod 移除，TabLayoutFingerprint 必失配 → 此依賴讓
            // SettingsPatch 連坐失敗 → 70 個 patch 全倒（morphe #50 實錘）。
            // RedirectBMTab 本身留待 Compose 底欄重新設計。
            addResourcesPatch,
            crashLogsPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("twitter")

            // 2026-11-26 移除三個舊 hook（12.46.1-prod 單一 Activity 架構 + 舊
            // preference 棧整體拆除，androguard 實查全 dex 0 命中）：
            // 1. SettingsFragmentFingerprint（<clinit> pref-key String[] 註冊表 +
            //    addPref 注入）——註冊表已消失，無消費者；
            // 2. SettingsPreferenceFingerprint（onPreferenceTreeClick 攔截
            //    pref_mod 點擊）——androidx.preference 已從宿主移除，新根條目
            //    點擊由 PikoSettingsEntry.invoke 直達 startSettingsActivity；
            // 3. AuthorizeAppActivity / UrlInterpreterActivity hook 群——兩個
            //    Activity 均已移除。設定宿主改為擴展自帶的 PikoSettingsActivity
            //    （SettingsResourcePatch 註冊 manifest）；piko 路由 deeplink
            //    （i/piko 等）改掛 MainActivity.onCreate，見下方。

            // piko 路由 deeplink（i/piko、r/pikosettings…）：宿主
            // UrlInterpreterActivity 已移除，http/https deeplink 改由
            // MainActivity 的 VIEW intent-filter 接收（與 HandleCustomDeepLinksPatch
            // 同一錨點、不同擴展方法，互不衝突）。
            PikoDeeplinkFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, $DEEPLINK_HOOK_CLASS->deeplink(Landroid/app/Activity;)Z",
            )

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$SSTS_DESCRIPTOR->load()V",
            )

            // execute ends.
        }
    }

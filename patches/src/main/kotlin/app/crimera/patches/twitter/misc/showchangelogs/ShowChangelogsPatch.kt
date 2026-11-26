/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.showchangelogs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch

// 2026-11-26：12.46.1-prod 單一 Activity 架構，舊 com.twitter.app.main.MainActivity 消失；
// 唯一 Activity 為 com.x.android.main.MainActivity（androguard 實查），且它**已有**
// onCreate(Bundle)V。舊邏輯 require(methods.none { onCreate }) + 新增 onCreate 方法
// 必炸。改為在既有 onCreate 開頭插入 showChangelog（與 HandleCustomDeepLinksPatch
// 對同一類 onCreate 的 addInstruction(0,...) 成功模式一致；p0=this、p1=Bundle）。
private object MainActivityFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)

@Suppress("unused")
val changelogsPatch =
    patch(
        name = "Show changelogs",
        description = "Shows changelogs when new a patch is installed.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            MainActivityFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, ${Constants.PATCHES_DESCRIPTOR}/Changelogs;->showChangelog(Landroid/app/Activity;)V",
            )

            enableSettings("showChangelogsPatchEnabled")
        }
    }

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.cleartrackingparams

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch

// https://github.com/FrozenAlex/revanced-patches-new
// 12.46.1: 舊 "shareParam"/"sessionToken" 字串已移除。分享追蹤參數現在統一由
// com.google.android.gms.internal.mlkit_vision_barcode.oe.b(String, attribution.j) 拼接
// "?s=<code>" / "&s=<code>"（com/x/share/impl/attribution/j 為分享目標 App 對應的追蹤代碼 enum）。
// 複製連結 (com/x/share/impl/f1.java:256 g()) 與 Intent 分享 (com/x/share/impl/m.java:63,149,162)
// 全部經過此方法，使其直接回傳 p0（原始 URL）即可清除追蹤參數。
internal object AddSessionTokenFingerprint : Fingerprint(
    parameters =
        listOf(
            "Ljava/lang/String;",
            "Lcom/x/share/impl/attribution/j;",
        ),
    returnType = "Ljava/lang/String;",
    strings =
        listOf(
            "s=",
        ),
)

@Suppress("unused")
val clearTrackingParamsPatch =
    patch(
        name = "Clear tracking params",
        description = "Removes tracking parameters when sharing links",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            AddSessionTokenFingerprint.method.addInstruction(0, "return-object p0")

            enableSettings("cleartrackingparams")
        }
    }

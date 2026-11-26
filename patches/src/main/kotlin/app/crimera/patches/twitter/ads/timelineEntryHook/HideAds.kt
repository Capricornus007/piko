/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object HidePromotedTrendFingerprint : Fingerprint(
    // 2026-11-26：12.46.1-prod 移除 LoganSquare（com.twitter.model.json.**），
    // timeline trend 改走 Apollo GraphQL。新錨點 ll1.c() 解析 __typename=="TimelineTrend"，
    // 欄位含 promoted_metadata/grouped_trends，與舊 JsonTimelineTrend 語意等價
    // （jadx com/x/android/main/fragment/{lc1,ll1}.java + classes7.dex 反組譯實錘）。
    definingClass = "Lcom/x/android/main/fragment/ll1;",
    name = "c",
    returnType = "Lcom/x/android/main/fragment/hl1;",
)

@Suppress("unused")
val hideAds =
    patch(
        name = "Remove Ads",
        description = "Removed promoted posts, trends and google ads",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            // Normal Ads.
            enableSettings("hideAds")

            // Google Ads.
            flagSettings("hideGoogleAds")

            // Promoted Trends
            val method = HidePromotedTrendFingerprint.method
            val instructions = method.instructions

            val return_obj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
            val return_loc = return_obj.location.index
            val return_reg = method.getInstruction<OneRegisterInstruction>(return_loc).registerA
            // ll1.c() 的回傳路徑是 new-instance v1 → return-object v1，物件暫存器就是
            // return_reg 本身。舊版用「最後 NEW_INSTANCE +3」另取 reg 的假設已失效
            // （該處已非 TwoRegisterInstruction，且 reg 與 return_reg 同為 v1 會誤殺）。
            // 用 findFreeRegister 取獨立 flag 暫存器，保持「兩暫存器不同」語意。
            val flag_reg = method.findFreeRegister(return_loc, listOf(return_reg))

            method.addInstructionsWithLabels(
                return_loc,
                """
                invoke-static {v$return_reg},${Constants.PATCHES_DESCRIPTOR}/TimelineEntry;->hidePromotedTrend(Ljava/lang/Object;)Z
                move-result v$flag_reg
                if-eqz v$flag_reg, :cond_1212
                const v$return_reg, 0x0
                """.trimIndent(),
                ExternalLabel("cond_1212", return_obj),
            )
        }
    }

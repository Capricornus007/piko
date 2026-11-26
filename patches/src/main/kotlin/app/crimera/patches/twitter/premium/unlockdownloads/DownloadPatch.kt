/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.unlockdownloads

import app.crimera.patches.twitter.entity.extMedia.MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// 2026-11-26 重寫（採礦報告 /tmp/mining_entity_link.md）：舊 tweetview
// mediaoptionssheet 大方法已隨 com.twitter.* 移除。新 premium 閘門在
// com/x/video/tab/n0.i(d9)（n0.java:688）：invoke-virtual a0.g()（訂閱檢查）
// → move-result vY → if-nez vY,:continue。const true 覆寫 vY 即繞過閘門。
// （jadx n0.java:682-713 + subscriptions/a0.java:69 實錘）
private object DownloadPatchFingerprint : Fingerprint(
    definingClass = "Lcom/x/video/tab/n0;",
    name = "i",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = "Lcom/x/subscriptions/a0;",
                name = "g",
                returnType = "Z",
            ),
        ),
    strings = listOf("download_video"),
)

// 2026-11-26：immersive bottom sheet 的可下載旗標改在 k8.java:161 以
// new l3(Float, Z, String, String) 建構（boolean = d() 可下載）。在
// invoke-direct 前把 boolean 參數暫存器 const 1 即強制顯示下載選項。
private object ImmersiveBottomSheetPatchFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/helpers/k8;",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                definingClass = "Lcom/x/video/tab/l3;",
                name = "<init>",
                parameters =
                    listOf(
                        "Ljava/lang/Float;",
                        "Z",
                        "Ljava/lang/String;",
                        "Ljava/lang/String;",
                    ),
                returnType = "V",
            ),
        ),
)

// 2026-11-26：可下載檢查統一走 s3.h()（isDownloadable getter）。實作三個：
// y4（video，前輪已重定位）、r4/u4（GIF 等其他型別，本輪補齊——前輪只做 y4
// 導致 GIF 仍鎖）。對每個 h() 的 IGET_BOOLEAN 後補 const true。
private object MediaEntityFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/y4;",
    name = "h",
    filters = OpcodesFilter.opcodesToFilters(Opcode.IGET_BOOLEAN),
)

private object GifMediaEntityFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/r4;",
    name = "h",
    filters = OpcodesFilter.opcodesToFilters(Opcode.IGET_BOOLEAN),
)

private object AltMediaEntityFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/u4;",
    name = "h",
    filters = OpcodesFilter.opcodesToFilters(Opcode.IGET_BOOLEAN),
)

// Credits to @iKirby
@Suppress("unused")
val downloadPatch =
    patch(
        name = "Download patch",
        description = "Unlocks the ability to download videos and gifs from Twitter/X",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            // 繞過 premium 閘門：a0.g() 的 move-result vY 之後 const 1，
            // if-nez vY,:continue 恆跳 continue，upsell 分支永不執行。
            DownloadPatchFingerprint.method.apply {
                val gateIndex = DownloadPatchFingerprint.instructionMatches[0].index
                val reg = getInstruction<OneRegisterInstruction>(gateIndex + 1).registerA
                addInstruction(
                    gateIndex + 2,
                    """
                    const/4 v$reg, 0x1
                    """.trimIndent(),
                )
            }

            MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.method.apply {
                val first_if2_loc = instructions.first { it.opcode == Opcode.IF_EQZ }.location.index
                val r3 = getInstruction(first_if2_loc).registersUsed[0]

                // remove premium restriction
                addInstructions(
                    first_if2_loc,
                    """
                    const v$r3, true
                    """.trimIndent(),
                )
            }

            // force downloadable：三個 s3.h() 實作（video/gif/其他）全部 const true
            listOf(MediaEntityFingerprint, GifMediaEntityFingerprint, AltMediaEntityFingerprint)
                .forEach { fp ->
                    val method3 = fp.method
                    val instructions3 = method3.instructions
                    val loc = instructions3.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
                    val r4 = method3.getInstruction<TwoRegisterInstruction>(loc).registerA

                    method3.addInstructions(
                        loc + 1,
                        """
                        const v$r4, true
                        """.trimIndent(),
                    )
                }

            // force add download option in immersive bottomsheet：
            // new l3(Float, Z, String, String) 的 boolean 參數（第 2 參）const 1。
            ImmersiveBottomSheetPatchFingerprint.method.apply {
                val ctorIndex = ImmersiveBottomSheetPatchFingerprint.instructionMatches[0].index
                val insn = getInstruction(ctorIndex)
                val boolReg =
                    when (insn) {
                        is FiveRegisterInstruction -> insn.registerE
                        is RegisterRangeInstruction -> insn.startRegister + 2
                        else -> throw IllegalStateException("l3 <init> 呼叫格式非預期")
                    }
                addInstruction(
                    ctorIndex,
                    """
                    const/4 v$boolReg, 0x1
                    """.trimIndent(),
                )
            }

            enableSettings("enableVidDownload")
        }
    }

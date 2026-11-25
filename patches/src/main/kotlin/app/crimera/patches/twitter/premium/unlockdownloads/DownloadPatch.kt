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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// [12.46.1 失配] 舊目標（tweetview mediaoptionssheet 大方法，含 "mediaEntity" +
// "media_options_sheet"）已隨 com.twitter.* 舊命名空間整個移除，"mediaEntity" 字串
// 在 APK 中不存在（dex 二進制 grep 確認）。新版的下載 premium 閘門改為
// com/x/video/tab/n0.java:688 的 ((com.x.subscriptions.a0) this.l.a).g() invoke 呼叫
// （非 IGET_BOOLEAN 欄位讀取），gif 類型分支也改由 MediaContentGif model 承接，
// 與本 fingerprint 假設的注入形狀（第一個 IF_EQ + 移除 IGET_BOOLEAN）不相容。
// 待重寫注入邏輯後再重新定位。
private object DownloadPatchFingerprint : Fingerprint(
    strings =
        listOf(
            "mediaEntity",
            "media_options_sheet",
        ),
)

// [12.46.1 失配] "mediaEntity" 字串已不存在；新版 com.x.media.download.d 下載器
// 內也沒有對應的 Z 回傳 premium/可下載檢查（閘門上移到 UI 層 n0.java:688）。
private object FileDownloaderFingerprint : Fingerprint(
    returnType = "Z",
    filters = OpcodesFilter.opcodesToFilters(Opcode.IF_EQZ),
    strings = listOf("mediaEntity", "url"),
)

// [12.46.1 失配] "captionsState" 字串已自 APK 移除（dex 二進制 grep 確認），
// 找不到等價的 immersive bottom sheet 建構子字串錨點。
// 新版 immersive 下載可用性改由 com/x/android/main/helpers/k8.java:162 以
// MediaContentVideo.isDownloadable 欄位直接建構 com.x.video.tab.l3 (ImmersiveSharedVideo)。
private object ImmersiveBottomSheetPatchFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    strings = listOf("captionsState"),
)

// [12.46.1 重定位] 舊定義類別 Lcom/twitter/model/json/core/JsonMediaEntity; 已隨
// com.twitter.* 命名空間移除。新等價物為 MediaContentVideo（com/x/models/y4.java），
// 其 h()（com/x/models/media/s3 介面的 isDownloadable getter）回傳欄位 j：
//   public final boolean h() { return this.j; }  // y4.java:189-192
// 下載點擊處以 s3Var2.h() 判斷可否下載（com/x/video/tab/n0.java:695）。
// 強制 h() 回傳 true 即「強制影片可下載」，與舊注入邏輯（最後一個 IGET_BOOLEAN
// 後補 const true）完全相容。
private object MediaEntityFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/y4;",
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

            DownloadPatchFingerprint.method.apply {

                val first_if_loc = instructions.first { it.opcode == Opcode.IF_EQ }.location.index
                val reg = getInstruction<TwoRegisterInstruction>(first_if_loc)
                val r1 = reg.registerA
                val r2 = reg.registerB

                // //add support for gif
                addInstructionsWithLabels(
                    first_if_loc + 1,
                    """
                       const/4 v$r2, 0x2
                       
                       if-eq v$r1, v$r2, :cond_1212
                    """,
                    ExternalLabel("cond_1212", instructions.first { it.opcode == Opcode.NEW_INSTANCE }),
                )

                // enable download for all media
                instructions.first { it.opcode == Opcode.IGET_BOOLEAN }.location.index.apply {
                    removeInstruction(this)
                    removeInstruction(this)
                }
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

            // force video downloadable
            val method3 = MediaEntityFingerprint.method
            val instructions3 = method3.instructions
            val loc = instructions3.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
            val r4 = method3.getInstruction<TwoRegisterInstruction>(loc).registerA

            method3.addInstructions(
                loc + 1,
                """
                const v$r4, true
                """.trimIndent(),
            )

            // force add download option in immersive bottomsheet
            val method4 = ImmersiveBottomSheetPatchFingerprint.method
            val instructions4 = method4.instructions

            val last_iput_loc = instructions4.last { it.opcode == Opcode.IPUT_BOOLEAN }.location.index
            val iput_reg = method4.getInstruction<OneRegisterInstruction>(last_iput_loc).registerA
            method4.addInstruction(
                last_iput_loc,
                """
                const v${iput_reg}, 0x1
                """.trimIndent(),
            )

            enableSettings("enableVidDownload")
        }
    }

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

// 2026-11-26：12.46.1-prod 移除舊 logger 描述字串（parse(...)/guessFileName(...)/
// setNotificationVisibility(...) 全 dex 0 命中）。新下載器是 com/x/network/g.b(
// Uri,String,String,Map,String,Z) → DownloadManager.Request，內部 o8.k("X/", fileName)
// + setDestinationInExternalPublicDir(DIRECTORY_DOWNLOADS, k)（jadx g.java:148-171 +
// disas2 smali 實錘）。"X/" 是該方法唯一 const-string，作錨點。
internal object DownloadPathFingerprint : Fingerprint(
    definingClass = "Lcom/x/network/g;",
    name = "b",
    parameters =
        listOf(
            "Landroid/net/Uri;",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/util/Map;",
            "Ljava/lang/String;",
            "Z",
        ),
    returnType = "Landroid/app/DownloadManager\$Request;",
    strings = listOf("X/"),
)

@Suppress("unused")
val changeDownloadDirPatch =
    patch(
        name = "Custom download folder",
        description = "Change the download directory for video downloads",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val GETFOLDER_DESCRIPTOR =
                "$PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"

            val PUBLICFOLDER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

            DownloadPathFingerprint.method.apply {
                // 檔名是參數 str3（smali v6），不再是任何 parse 的 move-result。
                // o8.k 是 "X/" 之後第一個 INVOKE_STATIC，{v4, v6}：v6 = 檔名。
                // 在 o8.k 之前把 v6 換成 getVideoFolder(fileName)，公共/私有兩個
                // 分支同時生效（兩分支都用 o8.k 的結果當檔名）。
                val xSlashIndex = DownloadPathFingerprint.stringMatches[0].index
                val o8k =
                    instructions
                        .first { it.location.index > xSlashIndex && it.opcode == Opcode.INVOKE_STATIC }
                val o8kIndex = o8k.location.index
                val fileNameReg =
                    when (o8k) {
                        is FiveRegisterInstruction -> o8k.registerD
                        is RegisterRangeInstruction -> o8k.startRegister + 1
                        else -> throw PatchException("Custom download folder: o8.k 呼叫格式非預期")
                    }

                // 公共分支：sget-object Environment.DIRECTORY_DOWNLOADS → 換成
                // getPublicFolder()（自訂公開資料夾）。
                val sgetIndex =
                    instructions
                        .first {
                            it.opcode == Opcode.SGET_OBJECT &&
                                (it as ReferenceInstruction).reference.toString()
                                    .contains("DIRECTORY_DOWNLOADS")
                        }
                        .location.index
                val dirReg = getInstruction<OneRegisterInstruction>(sgetIndex).registerA

                // 先插靠後的（sgetIndex+1），再插靠前的（o8kIndex）——
                // 前面的插入會讓後面的索引偏移，順序反了會插錯位置。
                addInstructions(
                    sgetIndex + 1,
                    """
                    $PUBLICFOLDER_DESCRIPTOR
                    move-result-object v$dirReg
                    """.trimIndent(),
                )
                addInstructions(
                    o8kIndex,
                    """
                    invoke-static {v$fileNameReg}, $GETFOLDER_DESCRIPTOR
                    move-result-object v$fileNameReg
                    """.trimIndent(),
                )
                enableSettings("enableDownloadFolder")
            }
        }
    }

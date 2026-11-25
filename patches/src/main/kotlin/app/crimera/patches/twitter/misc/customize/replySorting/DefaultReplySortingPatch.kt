/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.replySorting

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// 2026-11-26：12.46.1-prod 移除了 com/twitter/tweetview/focal/ui/replysorting 與
// "reply_sorting_enabled"/"reply_sorting"/"controller_data" 字串（dex 二進位 grep 已不存在）。
// 回覆排序 enum 改為 com.x.models.ff（原名 com.x.models.TimelineRankingMode，
// Recency/Relevance/Likes，見 com/x/cards/impl/feedback/a.java case 19 的 serializer 名）。
// 預設值在貼文詳情 VM com.x.postdetail.r 建構子中以 InstanceKeeper key
// "post_detail_reply_ranking_mode" 初始化，預設 ff.Relevance（classes7.dex r.<init>
// 指令 146-152：const-string → instancekeeper get → if-null → sget ff.Relevance → f3.c）；
// 使用者變更排序時走 r.Z(ff)（原 onReplyRankingModeChanged，由 com.x.postdetail.j.invoke 呼叫）。
private object ReplySortingDefaultFinderFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/r;",
    strings =
        listOf(
            "post_detail_reply_ranking_mode",
        ),
)

private object ReplySortingLastSelectedFinderFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/r;",
    name = "Z",
    parameters =
        listOf(
            "Lcom/x/models/ff;",
        ),
    returnType = "V",
)

@Suppress("unused")
val defaultReplySortingPatch =
    patch(
        name = "Customize default reply sorting",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            // 覆寫預設排序：r.<init> 中第一個讀取 ff enum 常數的 sget-object
            // （即 state flow 的預設值 ff.Relevance），改為由 piko 偏好設定提供。
            val defaultMethod = ReplySortingDefaultFinderFingerprint.method
            val defaultInstructions = defaultMethod.instructions
            val loc =
                defaultInstructions.first {
                    it.opcode == Opcode.SGET_OBJECT &&
                        (it.getReference<FieldReference>()?.definingClass == "Lcom/x/models/ff;")
                }.location.index
            val rClass = defaultMethod.getInstruction<ReferenceInstruction>(loc).reference as FieldReference
            val r0 = defaultMethod.getInstruction<OneRegisterInstruction>(loc).registerA
            defaultMethod.addInstructions(
                loc + 1,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->defaultReplySortFilter()Ljava/lang/String;
                move-result-object v$r0
                invoke-static {v$r0}, ${rClass.definingClass}->valueOf(Ljava/lang/String;)${rClass.definingClass}
                move-result-object v$r0
                """.trimIndent(),
            )

            // 記錄使用者最後選擇：r.Z(ff) 是排序變更的唯一入口，開頭把列舉名稱寫入 piko 偏好。
            val method2 = ReplySortingLastSelectedFinderFingerprint.method
            val ffParamRegister = method2.implementation!!.registerCount - 1
            method2.addInstructions(
                0,
                """
                invoke-virtual {v$ffParamRegister}, Lcom/x/models/ff;->name()Ljava/lang/String;
                move-result-object v0
                invoke-static {v0}, $PREF_DESCRIPTOR;->setReplySortFilter(Ljava/lang/String;)V
                """.trimIndent(),
            )
            enableSettings("defaultReplySortFilter")
        }
    }

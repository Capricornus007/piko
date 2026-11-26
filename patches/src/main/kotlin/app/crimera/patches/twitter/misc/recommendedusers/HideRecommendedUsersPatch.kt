/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.recommendedusers

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

// 2026-11-26：12.46.1-prod 移除 LoganSquare 的
// JsonProfileRecommendationModuleResponse；新等價模型是
// com.twitter.profile.api.ProfileFollowRecommendationResponse（欄位 d =
// recommendedUsers），消費點 com/x/peoplediscovery/profile/d.invokeSuspend
// （jadx d.java:129-157 + disas3 smali index 60 實錘）。推薦 sheet 的
// emptyList.isEmpty() 分支正好讓 hook 回 null 時不彈窗。
private object HideRecommendedUsersFingerprint : Fingerprint(
    definingClass = "Lcom/x/peoplediscovery/profile/d;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val hideRecommendedUsers =
    patch(
        name = "Hide Recommended Users",
        description = "Hide recommended users that pops up when you follow someone",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = HideRecommendedUsersFingerprint.method
            val instructions = method.instructions

            // 攔截 profile/api/f->d（recommendedUsers）的那一個 IGET_OBJECT——
            // 方法後段還有 f->a/e/b 的 IGET_OBJECT，用「最後一個」會攔錯。
            val check =
                instructions
                    .first {
                        it.opcode == Opcode.IGET_OBJECT &&
                            (it as ReferenceInstruction).reference.toString()
                                .contains("Lcom/twitter/profile/api/f;->d")
                    }
                    .location.index
            val reg = (method.getInstruction(check) as OneRegisterInstruction).registerA

            // 欄位宣告型別是 List，執行期是 kotlinx serialization 產生的 ArrayList，
            // 先 check-cast 再交給 hideRecommendedUsers(ArrayList)ArrayList。
            method.addInstructions(
                check + 1,
                """
                check-cast v$reg, Ljava/util/ArrayList;
                invoke-static {v$reg}, $PREF_DESCRIPTOR;->hideRecommendedUsers(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$reg
                """.trimIndent(),
            )

            enableSettings("hideRecommendedUsers")
        }
    }

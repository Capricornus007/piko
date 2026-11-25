/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.notificationtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 2026-11-26：12.46.1-prod 重寫了通知 tab 模型——
// 舊字串 android_ntab_verified_tab_enabled 與 const-string "all"/"mentions" 已從 dex 消失，
// tab 改為 com.x.notifications.tab.m1(notificationTabType, title) 物件、名稱走資源 ID
// （R.string.vit_notifications_tab_all / vit_notifications_tab_mentions）。
// tab 列表現由 Lcom/x/notifications/tab/s;-><init> 組裝進欄位 s.q（immutableList.a），
// 唯一消費點是 Lcom/x/notifications/tab/l;->invoke（case 2）：
// iget-object s.q → new com/arkivanov/decompose/router/pages/h;(I, List)。
// 全 dex 掃描確認只有 l;->invoke 以 iget-object 讀取 s.q，以此欄位存取作 fingerprint 錨點。
private object CustomiseNotificationTabsFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = "Lcom/x/notifications/tab/s;",
                type = "Lkotlinx/collections/immutable/implementations/immutableList/a;",
            ),
        ),
)

@Suppress("unused")
val customiseNotificationTabsPatch =
    patch(
        name = "Customize notification tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            CustomiseNotificationTabsFingerprint.method.apply {
                val index = CustomiseNotificationTabsFingerprint.instructionMatches[0].index
                val reg = getInstruction<OneRegisterInstruction>(index).registerA

                addInstructions(
                    index + 1,
                    """
                    invoke-static {v$reg}, $CUSTOMISE_DESCRIPTOR;->notificationTabs(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$reg
                    """.trimIndent(),
                )
                enableSettings("notificationTabCustomisation")
            }
        }
    }

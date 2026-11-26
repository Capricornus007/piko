/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.timelinetabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

// 2026-11-26：舊錨 "null cannot be cast to non-null type android.app.Activity"
// 與 int tab id 17/34 模型已在 12.46.1 消失。新等價物是 sealed class
// com.x.home.s（m.INSTANCE=ForYou、l.INSTANCE=Following），在子元件工廠
// com/x/dms/di/u.invoke() 決定建 ForYou/Following component（jadx u.java:564-636
// + disas3 smali index 1122 + findstr3 唯一字符串錨點，見
// /tmp/mining_timelinetabs_deep.md）。
private object CustomiseTimelineTabsFingerprint : Fingerprint(
    definingClass = "Lcom/x/dms/di/u;",
    name = "invoke",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
    filters =
        listOf(
            string("onTimelineJumpedToTopOnUserRequest"),
        ),
)

@Suppress("unused")
val customiseTimelineTabsPatch =
    patch(
        name = "Customize timeline top bar",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            CustomiseTimelineTabsFingerprint.method.apply {
                // 全方法唯一一處 d1.a 的 IGET_OBJECT（smali index 1122，v4 = tab type），
                // 在其後注入 timelineTab(Object)Object 做 m↔l 等價互換（舊 17↔34）。
                val igetIndex =
                    instructions
                        .first {
                            it.opcode == Opcode.IGET_OBJECT &&
                                (it as ReferenceInstruction).reference.toString()
                                    .contains("Lcom/x/home/d1;->a")
                        }
                        .location.index
                val reg = getInstruction<OneRegisterInstruction>(igetIndex).registerA

                addInstructions(
                    igetIndex + 1,
                    """
                    invoke-static {v$reg}, ${PREF_DESCRIPTOR};->timelineTab(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$reg
                    """.trimIndent(),
                )

                enableSettings("timelineTabCustomisation")
            }
        }
    }

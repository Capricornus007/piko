/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.exploretabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

// 2026-11-26：12.46.1-prod 的 JsonPageTabs 已消失，探索頁子 tab 改由
// 伺服器 GraphQL UrpPageFragment 提供，經 URP 框架轉成 UrpTimeline 列表。
// 新錨點 com/x/repositories/urp/b.a(rn1):List（classes7.dex，jadx b.java:21-65
// + disas3 81 條指令實錘，見 /tmp/mining_exploretabs_deep.md）。
private object CustomiseExploreTabsFingerprint : Fingerprint(
    definingClass = "Lcom/x/repositories/urp/b;",
    name = "a",
    parameters = listOf("Lcom/x/android/main/fragment/rn1;"),
    returnType = "Ljava/util/List;",
)

@Suppress("unused")
val customiseExploreTabsPatch =
    patch(
        name = "Customize explore tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseExploreTabsFingerprint.method
            val instructions = method.instructions

            // 找最後一個 ArrayList->add 之後的第一個 RETURN_OBJECT
            // （smali index 77，v0 = ArrayList<UrpTimeline>）。不能用 last
            // RETURN_OBJECT——會命中 index 80 的 emptyList 分支 v11；也不能用
            // index 14 的單 tab 分支（listOf 回傳值執行期型別非 ArrayList）。
            val lastAdd =
                instructions
                    .last {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            (it as ReferenceInstruction).reference.toString()
                                .contains("Ljava/util/ArrayList;->add(")
                    }
                    .location.index
            val returnIndex =
                instructions
                    .first { it.opcode == Opcode.RETURN_OBJECT && it.location.index > lastAdd }
                    .location.index
            val reg = method.getInstruction<OneRegisterInstruction>(returnIndex).registerA

            method.addInstructions(
                returnIndex,
                """
                invoke-static {v$reg}, $CUSTOMISE_DESCRIPTOR;->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$reg
                """.trimIndent(),
            )
            enableSettings("exploreTabCustomisation")
        }
    }

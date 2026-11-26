/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.sidebar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 2026-11-26 更正（採礦報告 /tmp/mining_customize.md）：先前 f91fa377 定義
// name="drawerItems" 是誤判——全 14 dex 沒有任何方法叫 drawerItems，那只是
// com/x/main/landing/v.java:11 的 Kotlin FunctionReferenceImpl 反射元數據字符串。
// 真正的方法本體是 v.invoke(Object)Object（CollectionsKt.listOfNotNull 組裝 26 項
// drawer items），definingClass 必須給（dex 有大量 invoke(Object)Object）。
private object customiseNavBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/landing/v;",
    name = "invoke",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val customiseSideBarPatch =
    patch(
        name = "Customize side bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = customiseNavBarFingerprint.method

            val instructions = method.instructions

            // 12.46.1 的 v.invoke() 用 new-array + aput-object 逐項填入 26 項
            // （無 FILLED_NEW_ARRAY_RANGE，舊寫法會 NoSuchElementException）。
            // 找最後一個 APUT_OBJECT 之後的 RETURN_OBJECT：v0 剛被 listOfNotNull()
            // 賦值，型別已是 Ljava/util/List;，可直接餵 sideBar()，無需 check-cast。
            val lastAputIndex = instructions.last { it.opcode == Opcode.APUT_OBJECT }.location.index
            val return_obj =
                instructions
                    .first { it.opcode == Opcode.RETURN_OBJECT && it.location.index > lastAputIndex }
                    .location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(return_obj).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->sideBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructionsWithLabels(return_obj, METHOD)
            enableSettings("sideBarCustomisation")
        }
    }

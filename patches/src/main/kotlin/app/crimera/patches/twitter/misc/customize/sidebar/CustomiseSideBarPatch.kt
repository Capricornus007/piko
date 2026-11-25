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

// 2026-11-26：12.46.1-prod 把方法改名 invoke → drawerItems、簽名改 (DrawerState)List，
// feature flag 字符串 android_global_navigation_top_level_monetization_enabled 已從 dex 消失
// （com/x/main/landing/v.java 的反射元數據實錘新名與簽名）。
private object customiseNavBarFingerprint : Fingerprint(
    name = "drawerItems",
    returnType = "Ljava/util/List",
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

            var filledNewArrIndex = instructions.last { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }.location.index
            val return_obj =
                instructions
                    .first { it.opcode == Opcode.RETURN_OBJECT && it.location.index > filledNewArrIndex }
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

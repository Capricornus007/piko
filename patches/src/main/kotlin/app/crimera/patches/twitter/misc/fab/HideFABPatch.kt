/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.fab

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

// 2026-11-27：X 12.46.1 把舊 android_compose_fab_menu_enabled 字串與
// com.x.main.n0.b() 顯示開關物理移除。真正的 Compose FAB 根入口是
// splitinstall/t.e(composer/fab/o, Function0, Modifier, Composer, I)，
// 開頭呼叫靜態 t.U(...) 決定是否繪製（dex idx 62）。開關開啟時直接 return-void
// 即跳過整棵 FAB Compose 樹，等價舊行為。
private object HideFABFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/play/core/splitinstall/t;",
    name = "e",
    returnType = "V",
    parameters =
        listOf(
            "Lcom/x/composer/fab/o;",
            "Lkotlin/jvm/functions/Function0;",
            "Landroidx/compose/ui/Modifier;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lcom/google/android/play/core/splitinstall/t;",
                name = "U",
            ),
        ),
)

@Suppress("unused")
val hideFABPatch =
    patch(
        name = "Hide FAB",
        description = "Adds an option to hide Floating action button",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = HideFABFingerprint.method
            val first = method.instructions.first()

            // 方法開頭插入：開關開啟 → 直接 return-void（跳過整棵 FAB Compose 樹）。
            // 此方法參數在 v22+（Compose 高位寄存器），開頭借用低位 v0 安全。
            method.addInstructionsWithLabels(
                0,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->hideFAB()Z
                move-result v0
                if-eqz v0, :piko_show_fab
                return-void
                """.trimIndent(),
                ExternalLabel("piko_show_fab", first),
            )
            enableSettings("hideFAB")
        }
    }

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 12.46.1-prod：settings root 改為 Kotlin 代碼構建，res/xml/settings_root.xml 已移除
// （見 SettingsResourcePatch.kt 容錯註解）。com.x.settings.j.c() 組裝
// List<com.x.settings.common.s>（每個 s 包一組 common.r 條目）後直接返回，
// 全 APK 唯一（androguard 實查 classes4.dex）。
// 注意：r 的 8 參構造器在位元碼中全部以 invoke-direct/range 調用
// （暫存器多，R8 選了 /range 變體），故 filter 必須同時接受兩種 opcode。
internal object SettingsRootListFingerprint : Fingerprint(
    definingClass = "Lcom/x/settings/j;",
    name = "c",
    returnType = "Ljava/util/List;",
    filters = listOf(
        methodCall(
            opcodes = listOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE),
            definingClass = "Lcom/x/settings/common/r;",
            name = "<init>",
        ),
    ),
)

private const val PIKO_SETTINGS_ENTRY_CLASS =
    "Lapp/morphe/extension/twitter/settings/PikoSettingsEntry;"

@Suppress("unused")
val settingsEntryPatch =
    patch(
        name = "Add settings entry",
        description = "Adds Piko settings entry to the native settings root list",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(sharedExtensionPatch)

        execute {
            val method = SettingsRootListFingerprint.method
            val index = method.indexOfFirstInstructionOrThrow { opcode == Opcode.RETURN_OBJECT }
            val listRegister = method.getInstruction<OneRegisterInstruction>(index).registerA

            // j.c() 返回的是 CollectionsKt.listOf 產物（不可變），故由擴展端
            // PikoSettingsEntry.addRootEntry 複製成 ArrayList、追加「Piko」區段後回傳新 List。
            method.addInstructions(
                index,
                """
                    invoke-static { v$listRegister }, $PIKO_SETTINGS_ENTRY_CLASS->addRootEntry(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$listRegister
                """,
            )
        }
    }

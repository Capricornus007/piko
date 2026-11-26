/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.inlinebar

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

// 2026-11-26：12.46.1-prod 移除 bookmarks_in_timelines_enabled 字符串，
// 舊錨點全 dex 消失。新 inline actions 組裝在 Compose 方法
// com/x/inlineactionbar/b.h(Function1,Function0,w1,Modifier,Composer,I)V
// （jadx b.java:535-628、disas3 smali index 81-103 實錘，見
// /tmp/mining_inlinebar_deep.md）。h() 內無 const-string，改用
// definingClass + name + 完整 parameters + returnType 定位。
private object CustomiseInlineBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/inlineactionbar/b;",
    name = "h",
    parameters =
        listOf(
            "Lkotlin/jvm/functions/Function1;",
            "Lkotlin/jvm/functions/Function0;",
            "Lcom/x/inlineactionbar/w1;",
            "Landroidx/compose/ui/Modifier;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
    returnType = "V",
)

@Suppress("unused")
val customiseInlineBarPatch =
    patch(
        name = "Customize Inline action Bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseInlineBarFingerprint.method
            val instructions = method.instructions

            // 方案 B：攔 CollectionsKt.sortedWith(...) 的回傳 List（smali v6），
            // 必須在 Compose cache 寫入 o0.w0() 之前——否則 recomposition 命中
            // 快取會拿到未過濾的 list，設定失效（報告 §3.3）。
            val sortedWithIndex =
                instructions
                    .first {
                        it.opcode == Opcode.INVOKE_STATIC &&
                            (it as ReferenceInstruction).reference.toString()
                                .contains("Lkotlin/collections/CollectionsKt;->sortedWith")
                    }
                    .location.index
            val resultIndex =
                instructions
                    .first {
                        it.opcode == Opcode.MOVE_RESULT_OBJECT &&
                            it.location.index == sortedWithIndex + 1
                    }
                    .location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(resultIndex).registerA

            // 防呆：確認注入點在 cache 寫入之前
            val cacheWrite =
                instructions
                    .first {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            (it as ReferenceInstruction).reference.toString()
                                .contains("Landroidx/compose/runtime/o0;->w0") &&
                            it.location.index > sortedWithIndex
                    }
                    .location.index
            require(resultIndex < cacheWrite) { "inlineBar 注入點必須在 Compose cache w0 之前" }

            method.addInstructions(
                resultIndex + 1,
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->inlineBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent(),
            )

            enableSettings("inlineBarCustomisation")
        }
    }

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.showpollresults

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

private object JsonCardInstanceDataFingerprint : Fingerprint(
    // 2026-11-26：12.46.1-prod 把 binding_values 的 Gson AutoValue Mapper 改為
    // Apollo GraphQL adapter，舊 JsonCardInstanceData$$JsonObjectMapper 在 16 dex
    // 全部消失。新解析鏈：Apollo binding_values → com.x.models.cards.o1.a() 返回
    // LinkedHashMap（與舊 binding_values Map 語意一致，dex 反組譯實錘）。
    // 注意 o1 是 R8 混淆名、版本特定，故加 returnType=Map 約束提升健壯性。
    definingClass = "Lcom/x/models/cards/o1;",
    name = "a",
    returnType = "Ljava/util/Map;",
    filters =
        listOf(
            opcode(Opcode.IGET_OBJECT),
            opcode(Opcode.MOVE_RESULT_OBJECT),
            opcode(Opcode.CHECK_CAST),
        ),
)

@Suppress("unused")
val showPollResultsPatch =
    patch(
        name = "Show poll results",
        description = "Adds an option to show poll results without voting",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = JsonCardInstanceDataFingerprint.method

            val loc =
                method.instructions
                    .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    .location.index

            // o1.a() 是無參數實例方法，Map 在 v0（即 p0）；舊版 parseField 用 p2。
            val pollDescriptor =
                "invoke-static {v0}, $PREF_DESCRIPTOR;->polls(Ljava/util/Map;)Ljava/util/Map;"

            method.addInstructions(
                loc + 1,
                """
                $pollDescriptor
                move-result-object v0
                """.trimIndent(),
            )

            enableSettings("enableShowPollResults")
        }
    }

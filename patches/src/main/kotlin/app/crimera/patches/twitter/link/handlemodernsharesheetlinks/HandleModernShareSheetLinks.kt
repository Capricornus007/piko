/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.handlemodernsharesheetlinks

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.is_11_40_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import java.util.logging.Logger

internal object NewShareSheetLinkFingerprint : Fingerprint(
    // 12.46.1：R8 把 sharesheet class 混淆成 com/x/dms/components/sharesheet/l，
    // 且 "tweet-" 與 "https://x.com/i/status/" 被拆到不同 method（舊版同 method
    // 才能 method 級匹配）。直接鎖定 class + 單一字串定位 method，避免不唯一。
    definingClass = "Lcom/x/dms/components/sharesheet/l;",
    strings = listOf("https://x.com/i/status/"),
)

internal object NewShareSheetLinkFingerprint2 : Fingerprint(
    strings =
        listOf(
            "https://x.com/i/status/",
            "https://x.com/i/lists/",
            "https://x.com/i/trending/",
        ),
)

@Suppress("unused")
val handleModernShareSheetLinks =
    patch(
        description = "Hooks links on modern share sheet ( after 11.4x.xx )",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(versionCheckPatch)

        execute {

            // Checks in case someone uses this patch pre-11.4x.xx
            if (is_11_40_or_greater) {
                val contextualPostClass = "Lcom/x/models/ContextualPost;"
                val dummy = "#dummyReg"
                val dummy2 = "#linkReg"
                var callStatement =
                    """
                    invoke-static {v$dummy,v$dummy2}, $PATCHES_DESCRIPTOR/links/Urls;->hookShareSheetLink(Lcom/x/models/ContextualPost;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$dummy2
                    """.trimIndent()

                var shareSheetField: MutableField
                var contextualPostField: MutableField

                NewShareSheetLinkFingerprint.apply {
                    val className = classDef
                    shareSheetField = classDef.fields.first { it.type.contains("components/sharesheet") }
                    contextualPostField =
                        mutableClassDefBy(shareSheetField.type).fields.first { it.type == contextualPostClass }

                    // 12.46.1：fingerprint 改用單一字串，故取 [0]（原 [1] 即 https://x.com/i/status/）
                    val strIndex = stringMatches[0].index

                    method.apply {
                        val dummyRegister =
                            getInstruction(indexOfFirstInstruction(strIndex, Opcode.CONST_4)).registersUsed[0]
                        val linkRegister = getInstruction(strIndex).registersUsed[0]

                        val freeRegister = findFreeRegister(strIndex + 1, listOf(linkRegister, dummyRegister))

                        val injectCode =
                            callStatement
                                .replace(dummy, dummyRegister.toString())
                                .replace(dummy2, linkRegister.toString())

                        addInstructions(
                            strIndex + 1,
                            """
                            move-object/from16 v$freeRegister, p0
                            iget-object v$dummyRegister, v$freeRegister, $shareSheetField
                            iget-object v$dummyRegister, v$dummyRegister, $contextualPostField
                            $injectCode
                            """.trimIndent(),
                        )
                    }
                }

                NewShareSheetLinkFingerprint2.method.apply {
                    val firstGoto = indexOfFirstInstruction(Opcode.GOTO)

                    val shareSheetWInstruction = instructions[indexOfFirstInstruction(firstGoto, Opcode.IGET_OBJECT)]
                    val shareSheetWRegister = shareSheetWInstruction.registersUsed[1]

                    val linkMergeIndex = getInstruction<BuilderOffsetInstruction>(firstGoto).target.location.index
                    val linkMergeInstruction = getInstruction(linkMergeIndex)
                    val linkRegister = linkMergeInstruction.registersUsed[0]
                    val dummyRegister = findFreeRegister(linkMergeIndex + 1, listOf(linkRegister, shareSheetWRegister))

                    val injectCode =
                        callStatement
                            .replace(dummy, dummyRegister.toString())
                            .replace(dummy2, linkRegister.toString())

                    addInstructionsWithLabels(
                        linkMergeIndex + 1,
                        """
                        if-eqz v$shareSheetWRegister, :piko_skip_share_hook
                        iget-object v$dummyRegister, v$shareSheetWRegister, $contextualPostField
                        $injectCode
                        :piko_skip_share_hook
                        nop
                        """.trimIndent(),
                    )
                }
            } else {
                return@execute Logger.getLogger(this::class.java.name).warning(
                    "The patch \"Hooks links on modern share sheet\" is force succeeded and does not work on any version below 11.40.\n",
                )
            }
        }
    }

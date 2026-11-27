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
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import java.util.logging.Logger

internal object NewShareSheetLinkFingerprint : Fingerprint(
    // 12.46.1: R8 renamed the sharesheet class to com/x/dms/components/sharesheet/l and
    // the two old anchors ("tweet-" + the status url) no longer live in one method.
    // Lock the class plus the single status string instead (only <init> contains it).
    definingClass = "Lcom/x/dms/components/sharesheet/l;",
    strings = listOf("https://x.com/i/status/"),
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
                NewShareSheetLinkFingerprint.method.apply {
                    // 12.46.1: the final link is stored into l->E right after being built
                    // (right after the o8.k prefix join / v5.getUrl branch). Hook there and
                    // pass ShareTarget.d -- the obfuscated ContextualPost (com.x.models.r1,
                    // whose toString still says "ContextualPost(canonicalPost=...)") -- to the
                    // extension as a plain Object. The extension resolves the author screen
                    // name reflectively, so no reference to the now-missing
                    // "Lcom/x/models/ContextualPost;" class is emitted into the dex.
                    val linkStoreIndex =
                        instructions.indexOfFirst {
                            it.opcode == Opcode.IPUT_OBJECT &&
                                it.getReference<FieldReference>()?.let { ref ->
                                    ref.definingClass == "Lcom/x/dms/components/sharesheet/l;" && ref.name == "E"
                                } == true
                        }
                    require(linkStoreIndex >= 0) { "12.46.1: l->E link store not found" }

                    val postLoadIndex =
                        instructions.indexOfFirst {
                            it.opcode == Opcode.IGET_OBJECT &&
                                it.getReference<FieldReference>()?.let { ref ->
                                    ref.definingClass == "Lcom/x/dms/components/sharesheet/l0;" && ref.name == "d"
                                } == true
                        }
                    require(postLoadIndex >= 0) { "12.46.1: ShareTarget.d post load not found" }

                    // IPUT_OBJECT registers: [source(value), destination(this)]
                    // IGET_OBJECT registers: [destination, source(object)]
                    val linkRegister = instructions[linkStoreIndex].registersUsed[0]
                    val shareTargetRegister = instructions[postLoadIndex].registersUsed[1]
                    val freeRegister =
                        findFreeRegister(linkStoreIndex, listOf(linkRegister, shareTargetRegister))

                    addInstructions(
                        linkStoreIndex,
                        """
                        iget-object v$freeRegister, v$shareTargetRegister, Lcom/x/dms/components/sharesheet/l0;->d:Lcom/x/models/r1;
                        invoke-static {v$freeRegister, v$linkRegister}, $PATCHES_DESCRIPTOR/links/Urls;->hookShareSheetLink(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$linkRegister
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

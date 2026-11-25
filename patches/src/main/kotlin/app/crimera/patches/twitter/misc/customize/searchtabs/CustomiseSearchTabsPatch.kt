/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.searchtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// 2026-11-26：12.46.1-prod 移除了 "search_features_media_tab_enabled" /
// "search_features_lists_search_enabled" feature flag（dex 二進位 grep 已不存在）。
// 搜尋分頁（Top/Latest/People/Media/Lists）改為在 com.x.search.result.e 建構子中
// 固定建立（e.java:163），經 com/x/urt/items/post/media/f1;->j([Ljava/lang/Object;)
// 打包成 immutableList 後存入欄位 J。字串錨點 "android_search_content_type_filter_enabled"
// 同時存在於 <init> 與 f()，故加 name = "<init>" 限縮。
private object CustomiseSearchTabsPatchFingerprint : Fingerprint(
    definingClass = "Lcom/x/search/result/e;",
    name = "<init>",
    strings =
        listOf(
            "android_search_content_type_filter_enabled",
        ),
)

@Suppress("unused")
val customiseSearchTabsPatch =
    patch(
        name = "Customize search tab items",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseSearchTabsPatchFingerprint.method
            val instructions = method.instructions

            // 定位建構子中建立分頁列表的 f1->j(Object...) 呼叫，緊接的 move-result-object
            // 即列表暫存器；過濾後須轉回 immutableList（欄位 J 的宣告型別）再存回。
            val tabsInvoke = instructions.first {
                it.opcode == Opcode.INVOKE_STATIC &&
                    it.getReference<MethodReference>()?.let { ref ->
                        ref.definingClass == "Lcom/x/urt/items/post/media/f1;" && ref.name == "j"
                    } == true
            }
            val tabsMethodRef = tabsInvoke.getReference<MethodReference>()!!
            val moveResultLoc = tabsInvoke.location.index + 1
            val r0 = (method.getInstruction(moveResultLoc) as OneRegisterInstruction).registerA

            method.addInstructions(
                moveResultLoc + 1,
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->searchTabs(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                invoke-interface {v$r0}, Ljava/util/List;->toArray()[Ljava/lang/Object;
                move-result-object v$r0
                invoke-static {v$r0}, $tabsMethodRef
                move-result-object v$r0
                """.trimIndent(),
            )
            enableSettings("searchTabCustomisation")
        }
    }

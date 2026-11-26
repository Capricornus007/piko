/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 12.46.1-prod：LoganSquare（com.twitter.model.json.**）已整包移除，timeline 改走
// Apollo GraphQL。共用解析 choke point 在 com.x.android.main.fragment（被
// HomeTimeline 等大多數 timeline operation 的 response adapter 共用）：
//  - ik1.c() 解析單個 timeline entry（entry_id/sort_index/content）→ gk1
//  - fe1.c() 解析單個 module item（entry_id/item/dispensable/tree_display）→ ee1
// androguard 實查 classes7.dex：兩者皆 public static、類內唯一。
// 注意：gk1 的 entryId 在欄位 c（非 a），擴展端 JsonTimelineEntry stub 需同步；
// ee1 的 entryId 仍在欄位 a，與既有 stub 一致。
private object TimelineEntryHookFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/fragment/ik1;",
    name = "c",
    returnType = "Lcom/x/android/main/fragment/gk1;",
)

private object TimelineModuleItemHookFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/fragment/fe1;",
    name = "c",
    returnType = "Lcom/x/android/main/fragment/ee1;",
)

val timelineEntryHookPatch =
    patch(
        description = "Hooks timeline object",
    ) {
        execute {
            // 舊 LoganSquare parse 經 R8 重用參數暫存器，回傳值恰在 p1；
            // 新的 Apollo adapter 為 static 方法，回傳暫存器是獨立 local，
            // 故改為讀取 return-object 的實際暫存器再注入（語義不變：以
            // checkEntry 結果替換解析產物，null 即過濾該 entry）。
            var methods = TimelineEntryHookFingerprint.method
            var instructions = methods.instructions

            var returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            var returnReg = methods.getInstruction<OneRegisterInstruction>(returnObj).registerA

            methods.addInstructions(
                returnObj,
                """
                invoke-static {v$returnReg}, ${PATCHES_DESCRIPTOR}/TimelineEntry;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;)Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;
                move-result-object v$returnReg
                """.trimIndent(),
            )

            methods = TimelineModuleItemHookFingerprint.method
            instructions = methods.instructions

            returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            returnReg = methods.getInstruction<OneRegisterInstruction>(returnObj).registerA

            methods.addInstructions(
                returnObj,
                """
                invoke-static {v$returnReg}, ${PATCHES_DESCRIPTOR}/TimelineEntry;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;)Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;
                move-result-object v$returnReg
                """.trimIndent(),
            )
            // ends.
        }
    }

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.profiletabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// 2026-11-26：12.46.1-prod 移除了舊的客戶端 profile tab 列表建構方法——
// 舊錨點字串 blue_business_affiliates_list_consumption_ui_enabled 已從 dex 消失
// （grep -al 於所有 base/*.dex 均無命中），tab 可用性改為伺服器驅動：
// List<com.x.profile.c1> 由導航設定（y0）/URT 帶入，全 dex 掃描確認沒有任何方法
// 以常數組裝該列表（僅剩 switch-map 與序列化輔助）。
// 客戶端最後一個 tab 列表組裝點是 Lcom/x/profile/timeline/e;-><init>（classes7.dex）：
// 它將傳入列表過濾（Highlights 依 flow c 條件）後收集進 ArrayList，
// 再作為第 4 個參數傳給 kotlinx/coroutines/flow/z1;->f 建立狀態流（欄位 e.v）。
// 該方法以此唯一字串定位（全 app 僅此一處）。
//
// 注意：擴展層 Customise.profiletabs(ArrayList) 仍以反射讀取項目的 "g" 欄位（舊模型
// 的 String key），而新模型項目是 c1 列舉（無 "g" 欄位）→ 會走 catch 分支安全地
// 原樣返回。要讓本補丁真正生效，擴展層需改為比對 c1 列舉名稱／getScribeSection()。
private object CustomiseProfileTabsFingerprint : Fingerprint(
    strings =
        listOf(
            "Combined timeline requires at least two sub-tabs",
        ),
)

@Suppress("unused")
val customiseProfileTabsPatch =
    patch(
        name = "Customize profile tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseProfileTabsFingerprint.method
            val instructions = method.instructions

            // tab 組裝迴圈內的 ArrayList.add（全方法僅此一處），其 receiver 即列表暫存器
            val addIndex =
                instructions.indexOfFirst {
                    it.opcode == Opcode.INVOKE_VIRTUAL &&
                        it.getReference<MethodReference>().let { r ->
                            r.definingClass == "Ljava/util/ArrayList;" && r.name == "add"
                        }
                }

            // 迴圈結束後第一個 z1;->f，ArrayList 作為初始值傳入；於呼叫前轉換列表
            val flowIndex =
                instructions.indexOfFirst {
                    it.location.index > addIndex &&
                        it.opcode == Opcode.INVOKE_STATIC &&
                        it.getReference<MethodReference>().let { r ->
                            r.definingClass == "Lkotlinx/coroutines/flow/z1;" && r.name == "f"
                        }
                }

            val listRegister =
                method.getInstruction<Instruction35c>(addIndex).registerC

            val METHOD =
                """
                invoke-static {v$listRegister}, $CUSTOMISE_DESCRIPTOR;->profiletabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$listRegister
                """.trimIndent()

            method.addInstructions(flowIndex, METHOD)
            enableSettings("profileTabCustomisation")
        }
    }

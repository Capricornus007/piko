/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.navbar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// 2026-11-26：12.46.1-prod 整個移除了 Blue 的 tab customization 功能——
// "tabCustomizationPreferences"、"subscriptions_feature_1008" 與類別前綴
// Lcom/twitter/subscriptions/tabcustomization/model/ 已全部從 dex 消失
// （grep -al 於所有 base/*.dex 二進制確認零命中），舊的四組 fingerprint 無目標可尋。
//
// 新架構：底部導覽列項目由 Lcom/x/main/landing/t0;-><init>（classes7.dex）組態——
// 列舉 com.x.navigation.vd（HOME/EXPLORE/GROK/NOTIFICATIONS/DM/COMMUNITIES/SPACES）
// 過濾（去除 COMMUNITIES/SPACES，GROK 依 android_webview_grok_tab_enabled）後
// 建成 LinkedHashMap<vd, m1>（tabData），作為第 4 個參數傳入
// LandingBadgesState（Lcom/x/main/api/landing/w0;）建構子。
// 手機與大螢幕導覽列皆消費 w0.d（com/x/main/k0.java:192 entrySet 迭代、:723 keySet）。
//
// 注入：於 w0.<init> 呼叫前以 navBar(Map) 依使用者偏好過濾鍵（vd 列舉名稱），
// 再把結果複製回 LinkedHashMap——因 w0 建構子的第 4 參數宣告為 LinkedHashMap，
// 直接傳入 navBar 回傳的 Map 型別會過不了 dex 驗證。
//
// 注意：piko_array_navbar_val 的舊鍵名 GUIDE/DMS 已對應不到新列舉 EXPLORE/DM
// （arrays.xml 不在本次修改範圍），需更新資源後該兩項才可被隱藏。
private object CustomiseNavBarFingerprint : Fingerprint(
    strings =
        listOf(
            "android_webview_grok_tab_enabled",
        ),
)

@Suppress("unused")
val customiseNavBarPatch =
    patch(
        name = "Customize Navigation Bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)

        dependsOn(
            settingsPatch,
        )

        execute {

            val method = CustomiseNavBarFingerprint.method
            val instructions = method.instructions

            // 定位 LandingBadgesState(w0) 建構呼叫（方法內唯一），第 4 個參數即 tabData
            val w0InitIndex =
                instructions.indexOfFirst {
                    it.opcode == Opcode.INVOKE_DIRECT &&
                        it.getReference<MethodReference>().let { r ->
                            r.definingClass == "Lcom/x/main/api/landing/w0;" && r.name == "<init>"
                        }
                }

            // w0.<init>(Map, I, Z, LinkedHashMap) 共 5 個引數（含 receiver），
            // 第 5 個引數（registerG）即 tabData LinkedHashMap
            val mapRegister =
                method.getInstruction<Instruction35c>(w0InitIndex).registerG
            val freeRegister =
                getFreeRegisterProvider(w0InitIndex, 1, mapRegister).getFreeRegister()

            method.addInstructions(
                w0InitIndex,
                """
                    invoke-static {v$mapRegister}, $CUSTOMISE_DESCRIPTOR;->navBar(Ljava/util/Map;)Ljava/util/Map;
                    move-result-object v$freeRegister
                    new-instance v$mapRegister, Ljava/util/LinkedHashMap;
                    invoke-direct {v$mapRegister, v$freeRegister}, Ljava/util/LinkedHashMap;-><init>(Ljava/util/Map;)V
                """,
            )

            enableSettings("navBarCustomisation")
            flagSettings("navbarFix")
        }
    }

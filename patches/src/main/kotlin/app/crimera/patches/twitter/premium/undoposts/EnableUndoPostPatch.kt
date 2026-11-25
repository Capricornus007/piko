/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.undoposts

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

// [12.46.1 失配] feature flag "subscriptions_feature_1003" 已自 APK 完全移除
// （dex 二進制 grep 確認；剩餘編號 flag：1001=自訂 App 圖示、1002=書籤/歷史、
// 1014=長文、labs_1004=編輯貼文，皆與 undo post 無關）。
// "allow_undo_tweet"/"allow_undo_replies"/"undo_tweet_timer" 偏好仍存在，但只被
// 設定畫面讀寫（com/x/settings/premium/undopost/d.java:24-26），composer 端
// 已無對應的 feature flag 檢查方法可匹配。undo 行為推測改由伺服器下發的
// SubscriptionDetails.undo_period/undo_count（thrift 欄位）驅動。
// 三個 fingerprint 均找不到等價物，勿硬凑字串；待確認新閘門後重寫。
private object UndoPost1Fingerprint : Fingerprint(
    returnType = "Z",
    filters = OpcodesFilter.opcodesToFilters(Opcode.MOVE_RESULT_OBJECT),
    strings =
        listOf(
            "subscriptions_feature_1003",
            "allow_undo_replies",
            "allow_undo_tweet",
        ),
)

// [12.46.1 失配] "userPreferences"、"draftTweet"、"subscriptions_feature_1003" 三個
// 字串均已不存在於 APK（dex 二進制 grep 確認）。
private object UndoPost2Fingerprint : Fingerprint(
    returnType = "Z",
    strings =
        listOf(
            "userPreferences",
            "draftTweet",
            "subscriptions_feature_1003",
            "allow_undo_replies",
            "allow_undo_tweet",
        ),
)

// [12.46.1 失配] 同 UndoPost1Fingerprint，flag 字串已不存在。
private object undoPost3Fingerprint : Fingerprint(
    returnType = "Landroid/content/Intent;",
    strings =
        listOf(
            "subscriptions_feature_1003",
        ),
)

@Suppress("unused")
val enableUndoPostPatch =
    patch(
        name = "Enable Undo Posts",
        description = "Enables ability to undo posts before posting",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->enableUndoPosts()Z"

            // flag check 1
            val method1 = UndoPost1Fingerprint.method
            val loc1 =
                method1
                    .instructions
                    .first { it.opcode == Opcode.IF_EQZ }
                    .location.index
            method1.addInstruction(loc1 - 1, PREF.trimIndent())

            // flag check 2
            val method2 = UndoPost2Fingerprint.method
            val loc2 =
                method2
                    .instructions
                    .first { it.opcode == Opcode.IF_EQZ }
                    .location.index
            method2.addInstruction(loc2 - 1, PREF.trimIndent())

            // flag check 3
            val method3 = undoPost3Fingerprint.method
            val loc3 =
                method3
                    .instructions
                    .filter { it.opcode == Opcode.IF_EQZ }[1]
                    .location.index
            method3.addInstruction(loc3, PREF.trimIndent())

            enableSettings("enableUndoPosts")
        }
    }

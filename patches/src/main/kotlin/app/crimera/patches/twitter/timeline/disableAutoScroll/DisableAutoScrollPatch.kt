/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel

// X 12.46.1：舊 cold-start 錨點（applicationManager / releaseCompletable /
// home_timeline_disable_home_scroll_position_restoration_on_cold_launch_enabled 等）
// 已從 dex 物理移除，全部改以 resume-position 決策與 cold-start 請求為錨。
//
// e.a(u)Z：是否要在 cold-start 恢復時間軸位置。
// 原始邏輯為 FOR_YOU + x_android_timeline_resume_position_enabled +
// 快取位置未逾 x_android_timeline_resume_position_max_age_minutes → true。
// 開關開啟時直接回 false，等同「不恢復位置、從頭開始」。
private object ResumePositionDecisionFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/features/e;",
    name = "a",
    returnType = "Z",
    parameters = listOf("Lcom/x/models/timelines/u;"),
    filters = listOf(
        string("x_android_timeline_resume_position_enabled"),
        string("x_android_timeline_resume_position_max_age_minutes"),
    ),
)

// h.e(h6,Z)V：cold-start For You 請求。z 會傳入 h.b(h6,z,...) 與快取狀態 b，
// z=true → 讀取 FOR_YOU 快取並用 VIEWPORT_AWARE_AUTO_REFRESH（保留捲動位置），
// z=false → 略過快取讀取並用 AUTO_REFRESH（從頭載入）。
// 開關開啟時把 p2 強制為 false，雙重確保不自動捲動。
private object ColdStartForYouRequestFingerprint : Fingerprint(
    definingClass = "Lcom/x/repositories/home/h;",
    name = "e",
    returnType = "V",
    parameters = listOf("Lcom/x/models/h6;", "Z"),
    filters = listOf(
        string("ColdStartForYouTimelineRequest"),
    ),
)

// 保留給 RestoreTimelinePosition.kt 的 HomeTimelineSaveFingerprint 參照。
// 該輔助檔已無任何 patch 呼叫，故此指紋不再被解析，僅為通過編譯而留。
@Suppress("unused")
internal object HomeTimelineRestoreFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("home_timeline_disable_home_scroll_position_restoration_on_cold_launch_enabled")
    )
)

@Suppress("unused")
val disableAutoScrollPatch =
    patch(
        name = "Disable auto timeline scroll on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            ResumePositionDecisionFingerprint.method.apply {
                val native = getInstruction(0)
                // v0 在原 idx 0/1 才首次寫入；開關關閉時跳回原邏輯，
                // 開啟時借用 v0 回 false（不恢復位置）。
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->disableAutoTimelineScroll()Z
                    move-result v0
                    if-eqz v0, :native
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                    ExternalLabel("native", native),
                )
            }

            ColdStartForYouRequestFingerprint.method.apply {
                val native = getInstruction(0)
                // p2 即 boolean 參數（對應 v11，原 idx 36 `move v6 v11` 讀取）。
                // 開關開啟時強制為 false；關閉時直接跳回原邏輯，p2 保持原值。
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->disableAutoTimelineScroll()Z
                    move-result v0
                    if-eqz v0, :native
                    const/4 p2, 0x0
                    """.trimIndent(),
                    ExternalLabel("native", native),
                )
            }

            enableSettings("disableAutoTimelineScroll")
        }
    }

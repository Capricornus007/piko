/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.live

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.patch

@Suppress("unused")
val hideLiveThreadsPatch =
    patch(
        name = "Hide Live Threads",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            // 2026-11-26：Fleets / Live Threads 功能已從 X 上游徹底移除（2021 年），
            // 12.46.1-prod 三方實錘（jadx 源碼 0 命中、16 dex 無模型類、僅剩遙測字面量）：
            // 舊 JsonFleetsTimelineResponse 類不存在，任何新錨點都只會 hook 到無關
            // 遙測/開關代碼。保留 patch 聲明與 settings 以維持依賴完整性，
            // execute 改 no-op，避免 fingerprint 失配連坐其他補丁。
            println("Hide Live Threads: 上游已移除此功能，patch 跳過（no-op）")
            enableSettings("hideLiveThreads")
        }
    }

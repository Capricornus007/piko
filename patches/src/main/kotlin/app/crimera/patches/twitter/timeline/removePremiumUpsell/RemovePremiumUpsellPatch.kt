/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.removePremiumUpsell

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
import app.morphe.patcher.patch.patch

@Suppress("unused")
val disablePremiumUpsellPatch =
    patch(
        name = "Remove premium upsell",
        description = "Removes premium upsells",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            // 2026-11-26：錨點字串 subscriptions_upsells_premium_home_nav 已改名
            // ..._offer_enabled 且移到 com.x.home.tabbed.s，舊 dex 注入點消失。
            // 改由 FeatureSwitchPatch.load() 統一註冊所有 premium upsell flag，
            // FeatureFlag 全局 hook getBoolean → flagInfo 覆寫（見 FeatureFlagPatch）。
            // 本 patch 保留為設定項註冊殼，不再做 dex 注入。
            enableSettings("removePremiumUpsell")
            flagSettings("removePremiumUpsell")
        }
    }

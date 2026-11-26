/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hideNavbarBadges

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

// 2026-11-26：12.46.1-prod 舊 BadgeableTabView.setBadgeNumber 消失（nav badge
// 改 Compose）。新資料類 com.x.main.api.landing.m1（TabData）構造子
// (String tooltip, int badgeCount, boolean hasUnreadContent)，欄位 a=badgeCount
// （jadx 源碼 com/x/main/api/landing/m1.java:13 實查）。p2 即 badgeCount，
// navbarBadgeCount(I)I 語意完整保留。
private object BadgeNumberFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/api/landing/m1;",
    name = "<init>",
    parameters = listOf("Ljava/lang/String;", "I", "Z"),
    returnType = "V",
)

@Suppress("unused")
val hideNavBarBadgesPatch =
    patch(
        name = "Hide badges from navigation bar icons",
        description = "Hides notification nudges & counts from navigation bar icons",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            BadgeNumberFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    invoke-static {p2}, ${Constants.PREF_DESCRIPTOR};->navbarBadgeCount(I)I
                    move-result p2
                    """.trimIndent(),
                )
                enableSettings("hideNavbarBadge")
            }
        }
    }

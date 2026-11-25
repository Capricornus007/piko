/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.searchsuggestions

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel

// 2026-11-26：12.46.1-prod 移除了本地搜尋建議 SQLite DB——"search_queries" 表、
// "findSearchQuery: "、"LOWER(query)=LOWER(?) AND ..." 在所有 dex 中皆已消失，
// 且全 APK 無任何 CREATE TABLE 建立搜尋相關資料表。
// 最近搜尋改以 kotlinx.serialization（com.x.models.search.RecentSearchData，
// 見 com/x/models/search/p.java）存入 DataStore key "search_recent_v3"
// （com/x/repositories/search/q.java:15），寫入唯一入口為
// q.c(RecentSearchData, SuspendLambda)（內部經 account.e case 14 寫 DataStore）。
private object SearchDbInsertFingerprint : Fingerprint(
    definingClass = "Lcom/x/repositories/search/q;",
    name = "c",
    parameters =
        listOf(
            "Lcom/x/models/search/r;",
            "Lkotlin/coroutines/jvm/internal/SuspendLambda;",
        ),
    returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val pauseSearchSuggestion =
    patch(
        name = "Pause search suggestions",
        description = "Search suggestions will not be saved locally",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            SearchDbInsertFingerprint.method.apply {

                val firstInstruction = getInstruction(0)

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->pauseSearchSuggestions()Z
                    move-result v0
                    if-eqz v0, :cond_1212
                    sget-object v0, Lkotlin/Unit;->a:Lkotlin/Unit;
                    return-object v0
                    """.trimIndent(),
                    ExternalLabel("cond_1212", firstInstruction),
                )

                enableSettings("pauseSearchSuggestions")
            }
        }
    }

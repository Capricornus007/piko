/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.moreProfileInfo

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.util.findElementByAttributeValueOrThrow

internal val fieldList = listOf("fast_follower_stat", "tweet_stat", "article_stat", "media_stat", "likes_stat")

internal val moreProfileInfoResourcePatch =
    patch {
        execute {
            // 2026-11-26：12.46.1-prod 移除了 res/layout/profile_details.xml
            //（profile 頁重構）。檔案消失時容錯跳過，避免連坐依賴本 patch 的
            // moreProfileInfoPatch（同 settings_root.xml 模式）。
            try {
                document("res/layout/profile_details.xml").use { editor ->
                    val statsContainer =
                        editor.childNodes.findElementByAttributeValueOrThrow(
                            "android:id",
                            "@id/stats_container",
                        )

                    fieldList.forEach {
                        val stat = editor.createElement("com.twitter.ui.tweet.TweetStatView")
                        stat.setAttribute("android:id", "@+id/$it")
                        stat.setAttribute("android:visibility", "gone")
                        stat.setAttribute("android:layout_width", "wrap_content")
                        stat.setAttribute("android:layout_height", "fill_parent")
                        stat.setAttribute("style", "@style/ProfileStatView")

                        statsContainer.appendChild(stat)
                    }
                }
            } catch (t: Throwable) {
                println("profile_details.xml 注入跳過：${t::class.simpleName}: ${t.message}")
            }
        }
    }

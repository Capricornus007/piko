/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.morphe.patcher.Fingerprint

// 2026-11-26：12.46.1-prod 已整個移除舊版 long-press 分享選單子系統：
//   - "Delete Status"、"conversational_replies_android_pinned_replies_creation_enabled"
//     在所有 base/*.dex 中皆已不存在（二進位 grep 驗證）。
//   - 目標類別套件 com.twitter.tweet.action.*、com.twitter.model.core.* 已從 APK 消失。
//   - 動作 enum 改為 com.x.models.x5（原名 com.x.models.PostActionType，見
//     com/x/models/x5.java 的 kotlinx serialization metadata），點擊分發改為
//     com.x.urt.items.post.options.a0.g(x5, coroutine)（"handleConfirmedOption"），
//     簽名不含 Context / TweetItem 參數，也無 IF_NE enum 比對，與
//     ShareMenuButtonOnClickHook 的注入結構不相容。
// 結論：此 fingerprint 無法以替換字串方式修復，整個 shareMenu hook 子系統
// （ActionEnumsFingerprint、ShareMenuButtonAddHook、ShareMenuButtonInitHook、
// ShareMenuButtonFuncCallFingerprint）需針對新 URT post options 架構重新設計。
object ShareMenuButtonFuncCallFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "OK",
            "Delete Status",
            "click",
            "tweet_analytics",
            "author_moderated_replies_author_enabled",
            "conversational_replies_android_pinned_replies_creation_enabled",
            "share_menu_click",
        ),
)

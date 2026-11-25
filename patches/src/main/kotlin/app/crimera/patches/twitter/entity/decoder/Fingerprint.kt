/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.decoder

import app.morphe.patcher.Fingerprint

// Twitter 12.46.1-prod removed the old "Tweet Info" debug dialog string.
// TIMELINE_ITEM_CLASS_NAME is currently write-only in Decoder.kt, so we anchor to the
// new ContextualPost model toString (com.x.models.r1) just to keep decoderEntity alive.
object TimelineItemDebugDialogInfoBuilderFingerprint : Fingerprint(
    strings = listOf("ContextualPost(canonicalPost="),
)

// Old anchor "https://x.com/%1\$s/status/%2\$d" no longer exists; the share sheet now
// operates on com.x.models.ContextualPost (com.x.models.r1, see
// HandleModernShareSheetLinks.kt which already hardcodes "Lcom/x/models/ContextualPost;").
object ContextualTweetPermaLinkBuilderFingerprint : Fingerprint(
    strings = listOf("ContextualPost(canonicalPost="),
)

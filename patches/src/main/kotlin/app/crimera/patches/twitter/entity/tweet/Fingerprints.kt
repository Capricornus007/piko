/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.tweet

import app.crimera.patches.twitter.utils.Constants.ENTITY_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val ENTITY_TWEET_DEFINING_CLASS = "${ENTITY_DESCRIPTOR}Tweet"

// Old anchor "https://x.com/%1\$s/status/%2\$d" was removed; the tweet model class is now
// com.x.models.u0 (CanonicalPost), identified by its toString template.
internal object TweetObjectFingerprint : Fingerprint(
    filters =
        listOf(
            string("CanonicalPost(id="),
        ),
)

internal object TweetUsernameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUsername",
)

internal object TweetProfileNameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetProfileName",
)

internal object TweetUserIdFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUserId",
)

internal object TweetExtMediaEntitiesListFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getExtendedMediaEntities",
)

internal object TweetInfoFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetInfo",
)

internal object TweetLongTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getLongText",
)

internal object TweetShortTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getShortText",
)

// 2026-11-26：12.46.1 作者資訊從 u0(CanonicalPost) 上移到更高層
// ni(author interface)：username=ni.D()、profile name=ni.getName()。
// 舊的「u0 上一個方法內兩個 String getter + static 合併」結構全 dex 不存在
// （jadx+dex smali+全 dex 引用掃描三方實錘，見 /tmp/mining_tweetnames_deep.md）。
// 拆成三個 Fingerprint，各自抽混淆後方法名寫回 extension sentinel。

// A) username source：ni.g() 的 smali idx 0 = invoke-interface ni->D()
internal object TweetUsernameGetterFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/ni;",
    name = "g",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Lcom/x/models/ni;",
                name = "D",
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lads_mobile_sdk/o8;",
                name = "k",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
        ),
)

// B) profile name source：ni.a() 的 smali idx 0 = invoke-interface ni->getName()
internal object TweetProfileNameGetterFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/ni;",
    name = "a",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Lcom/x/models/ni;",
                name = "getName",
                returnType = "Ljava/lang/String;",
            ),
        ),
)

// C) author accessor：u0.d() → mh（給 extension 兩段式反射用）
internal object TweetAuthorAccessorFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/u0;",
    name = "d",
    returnType = "Lcom/x/models/mh;",
)

// Old "EntityList{mEntities=" is gone; the entity list class is now com.x.models.text.g0
// (PostEntityList, fields: mentions/urls/media/hashtags/cashtags/timestamps).
// NOTE: media list is the 3rd List field ("media"), the old "first List field" heuristic
// in TweetEntityPatch.kt no longer picks it.
internal object TweetMediaEntityClassFingerprint : Fingerprint(
    strings = listOf("PostEntityList(mentions="),
)

// Old "NoteTweet(id=" / ", text=" are gone; the note tweet class is now
// com.x.models.notes.j (NotePostResult.NotePost, text = its only String field).
internal object LongTweetObjectFingerprint : Fingerprint(
    strings =
        listOf(
            "NotePost(postEntityList=",
        ),
)

// 12.46.1: CanonicalPost (com.x.models.u0) itself exposes getText()->String;
// the old intermediate text-object class (com.twitter.model.core.entity.* with
// getText()->CharSequence) was removed. Match the getter directly on u0 so
// TweetEntityPatch can inject its (unobfuscated) name into Tweet.getShortText.
internal object GetTextFromTextObjectFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/u0;",
    name = "getText",
    returnType = "Ljava/lang/String;",
)

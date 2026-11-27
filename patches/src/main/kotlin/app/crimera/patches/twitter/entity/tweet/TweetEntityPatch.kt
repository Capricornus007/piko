/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.tweet

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeString
import app.crimera.utils.changeStringAt
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val tweetEntityPatch =
    patch(
        description = "For tweet entity reflection",
    ) {
        execute {
            // 2026-11-26：作者方法從 u0 上移到 ni(author)，舊 TweetNamesFingerprint
            // 的 [0]/[2] 結構已不存在。改從兩個新 Fingerprint 各抽 [0] 的方法名
            // （D=username、getName=profile name），並抽 u0.d() 寫入 authorAccessor
            // sentinel 供 extension 兩段式反射（見 /tmp/mining_tweetnames_deep.md）。
            val userNameMethod =
                TweetUsernameGetterFingerprint
                    .instructionMatches[0].instruction.getReference<MethodReference>()!!
            val profileNameMethod =
                TweetProfileNameGetterFingerprint
                    .instructionMatches[0].instruction.getReference<MethodReference>()!!

            // 用 changeString 按 sentinel 內容匹配（非槽位順序）——extension 的
            // getTweetUsername/ProfileName 改兩段式反射後各有兩個字符串，
            // changeFirstString 會改錯槽位。author accessor 方法名從 dex 的
            // u0.d() 抽出（混淆名會變，不硬編碼）。
            val authorAccessorName = TweetAuthorAccessorFingerprint.method.name
            TweetUsernameFingerprint.changeString("authorAccessorMethod", authorAccessorName)
            TweetUsernameFingerprint.changeString("userNameMethod", userNameMethod.name)
            TweetProfileNameFingerprint.changeString("authorAccessorMethod", authorAccessorName)
            TweetProfileNameFingerprint.changeString("profileNameMethod", profileNameMethod.name)

            val tweetObjectMethods = TweetObjectFingerprint.classDef.methods

            // 12.46.1: CanonicalPost (u0) has getText()->String directly; there is no
            // intermediate text-object class anymore. Inject the (unobfuscated) getter
            // name so Tweet.getShortText() can call it straight on the tweet object.
            val getTextObjectFromTweetObjectMethodName = GetTextFromTextObjectFingerprint.method.name
            TweetShortTextFingerprint.changeFirstString(getTextObjectFromTweetObjectMethodName)

            // 12.46.1: CanonicalPost(u0) 已無回傳原始 J 的方法；原本的
            // .first { it.returnType == "J" } 在 12.46.1 必然拋
            // NoSuchElementException（CI 的 "Collection contains no element"
            // 就是這裡）。extension 端 getTweetUserId() 做 (Long) 轉型，
            // 反射需要一個回傳 boxed java.lang.Long 的方法；u0 上唯一符合的
            // 是 n()（selfThreadId, field h: Ljava/lang/Long;）。改選它讓
            // patch 能套用、反射不崩。
            val getTweetUserIdMethod =
                tweetObjectMethods
                    .first { it.returnType == "Ljava/lang/Long;" }
                    .name
            TweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)

            // 12.46.1：舊的「4 個 IGET_OBJECT + RETURN_OBJECT」accessor 形態已物理消失
            // （全 dex 掃描零命中）。新的 media accessor 是 u0.g()，直接回傳
            // Lkotlinx/collections/immutable/c;——它是個 interface，继承了
            // Ljava/util/List;，所以 extension 端可以直接 cast 成 List，不必再走
            // 舊的「二段式 superclass + extMediaList 欄位」反射。
            // 這裡仍把方法名注入 sentinel：extension 的 getExtendedMediaEntities()
            // 是靠 super.getMethod("mediaMethod") 反射呼叫的，字串槽位必須保留。
            val getMediaObjectMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType == "Lkotlinx/collections/immutable/c;" }
                    ?.name
                    ?: throw PatchException(
                        "getMediaObject not found：u0 上沒有回傳 Lkotlinx/collections/immutable/c; 的 accessor",
                    )
            TweetExtMediaEntitiesListFingerprint.changeFirstString(getMediaObjectMethod)

            // 12.46.1: g0(PostEntityList) 有 7 個 List 欄位，media 是第 3 個
            // （Fingerprints.kt 註明）。舊的 first { contains("List") } 會選到
            // mentions，讓 getExtendedMediaEntities 抓錯欄位。
            val extMediaListField =
                TweetMediaEntityClassFingerprint.classDef.fields
                    .filter { it.type == "Ljava/util/List;" }
                    .elementAt(2)
                    .name
            TweetExtMediaEntitiesListFingerprint.changeStringAt(1, extMediaListField)

            // 2026-11-28：note tweet（長文）二段式注入移除。12.46.1 全 dex 沒有
            // 任何方法/欄位回傳或持有 note tweet 內容物件（notes/j 只由
            // kotlinx.serialization 反序列化建立；u0 上也沒有 *notetweet* accessor），
            // 舊的 `firstOrNull { returnType.contains("notetweet") }` 必然找不到而
            // 拋 "getNoteTweetMethod not found"，連帶 Native translator / Share Tweet
            // as Image 兩個 dependent patch 一起失敗。
            // 上游已把 note tweet 內文合併進 CanonicalPost(u0).getText()（就是上面
            // getShortText 注入的 GetTextFromTextObjectFingerprint 同一入口），
            // extension 的 Tweet.getLongText() 已改為複用該入口，故此處不再注入
            // noteTweetMethod / longTextField 兩個死槽位。功能零損失。

            // 2026-11-27：舊的 com.twitter.database.legacy.tdbh(TweetInfo object) 與
            // supplemental_language 欄位在 12.46.1 已物理移除。language 現在直接是
            // CanonicalPost(u0) 自己的 String field，改由 tweetInfoEntityPatch 從
            // u0.toString() 的 ", language=" 錨點動態抽出並寫入 TweetInfo.getLang()。
            // 這裡舊的 first { it.type == className } 鏈在 12.46.1 必然第一個就失敗，
            // 故整段移除。
        }
    }

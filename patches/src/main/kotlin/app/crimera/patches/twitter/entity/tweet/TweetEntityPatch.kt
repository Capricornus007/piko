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
import com.android.tools.smali.dexlib2.Opcode
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

            val getTweetUserIdMethod =
                tweetObjectMethods
                    .first { it.returnType == "J" }
                    .name
            TweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)

            val getMediaObjectMethod =
                tweetObjectMethods.firstOrNull { methodDef ->
                    methodDef.implementation
                        ?.instructions
                        ?.map { it.opcode }
                        ?.toList() ==
                        listOf(
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.IGET_OBJECT,
                            Opcode.RETURN_OBJECT,
                        )
                } ?: throw PatchException("getMediaObject not found")
            TweetExtMediaEntitiesListFingerprint.changeFirstString(getMediaObjectMethod.name)

            val extMediaListField =
                TweetMediaEntityClassFingerprint.classDef.fields
                    .first { it.type.contains("List") }
                    .name
            TweetExtMediaEntitiesListFingerprint.changeStringAt(1, extMediaListField)

            val getNoteTweetMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType.contains("notetweet") }
                    ?.name
                    ?: throw PatchException("getNoteTweetMethod not found")
            TweetLongTextFingerprint.changeFirstString(getNoteTweetMethod)

            val longTextField =
                LongTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name
            TweetLongTextFingerprint.changeStringAt(1, longTextField)

            // 2026-11-27：舊的 com.twitter.database.legacy.tdbh(TweetInfo object) 與
            // supplemental_language 欄位在 12.46.1 已物理移除。language 現在直接是
            // CanonicalPost(u0) 自己的 String field，改由 tweetInfoEntityPatch 從
            // u0.toString() 的 ", language=" 錨點動態抽出並寫入 TweetInfo.getLang()。
            // 這裡舊的 first { it.type == className } 鏈在 12.46.1 必然第一個就失敗，
            // 故整段移除。
        }
    }

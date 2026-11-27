/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.tweetInfo

import app.crimera.patches.twitter.entity.tweet.TweetObjectFingerprint
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val tweetInfoEntityPatch =
    patch(
        description = "For tweet info entity reflection",
    ) {
        execute {
            // 2026-11-27：12.46.1 已移除舊 TweetInfo object（com.twitter.database.legacy.tdbh）
            // 與 supplemental_language 欄位。language 現在直接是 CanonicalPost(u0) 的
            // String field。從 u0.toString() 的 ", language=" 錨點後取第一個 String
            // IGET_OBJECT 即為混淆後的字段名，寫入 extension 的 "tweetLang" sentinel。
            val code = TweetObjectFingerprint.method.instructions.toList()
            val langAnchor =
                code.indexOfFirst {
                    it.opcode == Opcode.CONST_STRING &&
                        it.getReference<StringReference>()?.string == ", language="
                }
            if (langAnchor < 0) {
                throw app.morphe.patcher.patch.PatchException("u0.toString 找不到 , language= 錨點")
            }
            val langField =
                code
                    .drop(langAnchor + 1)
                    .first {
                        it.opcode == Opcode.IGET_OBJECT &&
                            it.getReference<FieldReference>()?.type == "Ljava/lang/String;"
                    }.getReference<FieldReference>()!!.name
            TweetLangFingerprint.changeFirstString(langField)
        }
    }

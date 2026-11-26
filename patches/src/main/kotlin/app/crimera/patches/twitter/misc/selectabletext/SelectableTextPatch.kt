/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.selectabletext

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.patch
import org.w3c.dom.Element

private val selectableTextResourcePatch =
    patch {
        execute {
            val ids =
                listOf(
                    "user_name",
                    "user_bio",
                ).map { "@id/$it" }

            // 2026-11-26：12.46.1-prod 移除了 res/layout/profile_details.xml
            //（profile 頁重構），容錯跳過避免連坐（同 settings_root.xml 模式）。
            try {
                document("res/layout/profile_details.xml").use { editor ->
                    val texts = editor.getElementsByTagName("com.twitter.ui.components.text.legacy.TypefacesTextView")
                    for (i in 0 until texts.length) {
                        val text = texts.item(i) as Element
                        val id = text.getAttribute("android:id")

                        if (ids.contains(id)) {
                            text.setAttribute("android:textIsSelectable", "true")
                        }
                    }
                }
            } catch (t: Throwable) {
                println("profile_details.xml 注入跳過：${t::class.simpleName}: ${t.message}")
            }
        }
    }

@Suppress("unused")
val selectableTextPatch =
    patch(
        name = "Selectable Text",
        description = "Makes bio and username selectable",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, selectableTextResourcePatch)

        execute {
            enableSettings("selectableText")
        }
    }

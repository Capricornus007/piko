/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.getNode
import org.w3c.dom.Element

internal val settingsResourcePatch =
    patch {
        execute {
            // replace the keyword `ripped` from version name back to original format.
            val versionName = packageMetadata.versionName
            val rippedKeyword = "-ripped"
            if (rippedKeyword in versionName) {
                document("AndroidManifest.xml").use { document ->
                    val manifestElement = document.getNode("manifest") as Element
                    manifestElement.setAttribute("android:versionName", versionName.replace(rippedKeyword, ""))
                }
            }

            // 2026-11-26：12.46.1-prod 移除了 res/xml/settings_root.xml（settings root 改為
            // com.x.settings.j.c() 代碼構建，見該檔 259-264 行的 List<common.s> 組裝）。
            // 此注入點消失時若直接拋例外，本 patch 失敗 → 所有依賴 settingsPatch 的
            // patch 全部連坐（morphe #40 實測 71 個失敗）。改為容錯跳過；
            // settings 列表的新入口由 SettingsEntryPatch（j.c() hook）補上。
            try {
                document("res/xml/settings_root.xml").use { editor ->
                    val parent = editor.getElementsByTagName("PreferenceScreen").item(0) as Element

                    val prefMod = editor.createElement("Preference")
                    prefMod.setAttribute("android:icon", "@drawable/ic_vector_settings_stroke")
                    prefMod.setAttribute("android:title", "@string/piko_title_settings")
                    prefMod.setAttribute("android:key", "pref_mod")
                    prefMod.setAttribute("android:order", "110")

                    parent.appendChild(prefMod)
                }
            } catch (_: PatchException) {
                // settings_root.xml 已不存在（新版代碼構建 settings），跳過 XML 注入。
                // 注意：patcher 的 document() 把 IOException 包成 PatchException 拋出
                // （morphe #44 實錘——catch FileNotFoundException 永遠接不到）。
            }

            // credits aero
            // 2026-11-26：main_activity_app_bar.xml 同樣已從 12.46.1-prod 移除（arsc 實查），
            // 同樣容錯跳過，避免連坐。
            try {
                document("res/layout/main_activity_app_bar.xml").use { editor ->
                val parent = editor.getElementsByTagName("FrameLayout").item(1) as Element

                val sideBtn =
                    editor.createElement("app.morphe.extension.twitter.settings.widgets.PikoSettingsButton")
                sideBtn.setAttribute("android:text", "Piko")
                sideBtn.setAttribute("android:textAllCaps", "false")
                sideBtn.setAttribute("android:background", "?android:attr/selectableItemBackground")
                sideBtn.setAttribute("android:drawableLeft", "@drawable/ic_vector_settings_stroke")
                sideBtn.setAttribute("android:drawableTint", "?attr/coreColorPrimaryText")
                sideBtn.setAttribute("android:layout_height", "@dimen/docker_height")
                sideBtn.setAttribute("android:layout_width", "wrap_content")
                sideBtn.setAttribute("android:padding", "10dp")
                sideBtn.setAttribute("android:layout_marginBottom", "2.3dp")
                sideBtn.setAttribute("android:layout_gravity", "bottom|right")

                parent.appendChild(sideBtn)
                }
            } catch (_: PatchException) {
                // main_activity_app_bar.xml 已不存在（12.46.1-prod arsc 實查），跳過
            }

            // Add piko settings to the launcher icon long-press shortcuts
            document("res/xml/shortcuts.xml").use {
                val extraElement =
                    it.createElement("extra").apply {
                        setAttribute("android:name", "shortcut")
                        setAttribute("android:value", "settings")
                    }
                val intentElement =
                    it.createElement("intent").apply {
                        setAttribute("android:targetPackage", "com.twitter.android")
                        setAttribute("android:action", "android.intent.action.VIEW")
                        setAttribute("android:data", "https://x.com/i/piko/")
                        setAttribute("android:targetClass", "com.twitter.deeplink.implementation.UrlInterpreterActivity")
                        appendChild(extraElement)
                    }
                val shortcutElement =
                    it.createElement("shortcut").apply {
                        setAttribute("android:icon", "@drawable/ic_vector_settings_shortcut")
                        setAttribute("android:enabled", "true")
                        setAttribute("android:shortcutId", "settings")
                        setAttribute("android:shortcutShortLabel", "@string/piko_title_settings")
                        setAttribute("android:shortcutLongLabel", "@string/piko_title_settings")
                        appendChild(intentElement)
                    }
                it.documentElement.insertBefore(shortcutElement, it.documentElement.firstChild)
            }

            copyResources(
                "twitter/settings",
                ResourceGroup("drawable", "ic_vector_settings_shortcut.xml"),
            )
        }
    }

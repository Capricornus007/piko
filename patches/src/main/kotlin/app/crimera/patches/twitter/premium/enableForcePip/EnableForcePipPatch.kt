/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.enableForcePip

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

// [12.46.1 失配] feature flag "android_immersive_media_player_native_pip_enabled" 已自
// APK 完全移除（dex 二進制 grep 確認，x_android_* 新命名體系中也無 pip/immersive player
// 相關 flag），找不到等價字串錨點。
// 新版 PiP 行為：MainActivity.java:86-90 僅依系統功能 android.software.picture_in_picture
// 決定使用 DefaultPipModeController（com/x/media/playback/pip/d.java）或 no-op 控制器；
// 關閉 App 自動進入 PiP 改由 d.java:202 的 setAutoEnterEnabled(oVar != null) 控制，
// 用戶端已無 feature flag 閘門（功能形同預設開啟）。若仍需強制開啟，須改為對
// d.h()（回傳 PictureInPictureParams）注入 setAutoEnterEnabled(true)，屬注入邏輯重寫。
private object EnableForcePip1Fingerprint : Fingerprint(
    strings =
        listOf(
            "impl",
            "unsupported",
            "android_immersive_media_player_native_pip_enabled",
        ),
)

// [12.46.1 失配] 同上，flag 字串已不存在。
private object EnableForcePip2Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object",
    strings = listOf("android_immersive_media_player_native_pip_enabled"),
)

@Suppress("unused")
val enableForcePipPatch =
    patch(
        name = "Enable PiP mode automatically",
        description = "Enables PiP mode when you close the app",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->enableForcePip()Z"

            val methods1 = EnableForcePip1Fingerprint.method
            val first_if_nez_loc =
                methods1.instructions
                    .first { it.opcode == Opcode.IF_NEZ }
                    .location.index
            methods1.addInstruction(first_if_nez_loc - 1, PREF)

            val methods2 = EnableForcePip2Fingerprint.method
            val first_sget_loc =
                methods2.instructions
                    .first { it.opcode == Opcode.SGET_OBJECT }
                    .location.index
            methods2.addInstruction(first_sget_loc + 2, PREF)

            enableSettings("enableForcePip")
        }
    }

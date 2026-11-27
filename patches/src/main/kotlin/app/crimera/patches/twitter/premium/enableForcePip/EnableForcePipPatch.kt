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
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode

// 2026-11-27 覆核（/tmp/recheck_enableforcepip.md）：PiP 未被物理移除，
// 底層 com/x/media/playback/pip/ 15 個 class 完整。舊 flag 消失但失效的是
// UI 閘門：com/x/video/tab/n0.v() 組裝 ShowVideoOptions 時把第 2 個 Z
// (isPictureInPictureAvailable) 寫死 false（smali index 53 const/4 v2 #0），
// 下游 ha.m() 讀 m9.b=false 就不加入 j9.PictureInPicture 選項。
// 改為用偏好值覆蓋該 boolean，保留舊「偏好開→強制 PiP」語意。
private object EnableForcePipFingerprint : Fingerprint(
    definingClass = "Lcom/x/video/tab/n0;",
    name = "v",
    parameters = listOf("Lcom/x/media/playback/settings/persistent/h;"),
    returnType = "Lcom/x/video/tab/m9;",
)

// 自動進入 PiP：d.h() 的 setAutoEnterEnabled 原本吃 v4（o==null 時為 0）。
// v5 自 index 19 const/4 #1 起未被改寫（index 152 也用 v5），把引數 v4→v5
// 即 setAutoEnterEnabled(true)，指令長度不變、不破壞 jump offset。
private object EnableForcePipAutoEnterFingerprint : Fingerprint(
    definingClass = "Lcom/x/media/playback/pip/d;",
    name = "h",
    parameters = listOf("Lcom/x/media/playback/pip/o;"),
    returnType = "Landroid/app/PictureInPictureParams;",
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

            // (1) UI 閘門：n0.v 的 index 53 = const/4 v2 #0（寫死的 false），
            //     後面注入偏好值：開→v2=1 顯示 PiP 選項，關→0 維持原樣。
            //     v2 在 53 與建構子 56 之間未被改寫，注入安全。
            val ui = EnableForcePipFingerprint.method
            check(ui.instructions[53].opcode == Opcode.CONST_4) {
                "EnableForcePip: n0.v 第 53 條指令不再是 const/4，請重新採礦"
            }
            ui.addInstruction(54, PREF)
            ui.addInstruction(55, "move-result v2")

            // (2) 自動進入：d.h 的 index 151 = setAutoEnterEnabled(v4)，
            //     換成 v5（恆為 1）force true。不改 x00.invoke 的
            //     hasSystemFeature——那是系統能力檢查，force 會在非 PiP 裝置出事。
            val auto = EnableForcePipAutoEnterFingerprint.method
            check(auto.instructions[151].opcode == Opcode.INVOKE_VIRTUAL) {
                "EnableForcePip: d.h 第 151 條指令不再是 invoke-virtual，請重新採礦"
            }
            auto.replaceInstruction(
                151,
                "invoke-virtual {v0, v5}, " +
                    "Landroid/app/PictureInPictureParams\$Builder;->setAutoEnterEnabled(Z)" +
                    "Landroid/app/PictureInPictureParams\$Builder;",
            )

            enableSettings("enableForcePip")
        }
    }

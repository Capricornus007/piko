/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.featureFlag.featureFlagPatch

import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// 2026-11-26：12.46.1-prod 重構 feature switches（舊錨點字串
// "feature_switches_configs_crashlytics_enabled" 已從全 dex 消失，androguard 實查）。
// 新系統在 com.x.featureswitches.*，布林旗標讀取統一走
// FeatureSwitchesRepositoryImpl.getBoolean(String, Z)Z（peekBoolean 為不觸發
// fetch 的變體，不 hook）。方法體：倉庫查詢 → move-result-object →
// instanceof Boolean → booleanValue → return；在 move-result-object 後攔截
// 返回值交給 flagInfo 覆寫。
private object FeatureFlagFingerprint : Fingerprint(
    definingClass = "Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;",
    name = "getBoolean",
    parameters = listOf("Ljava/lang/String;", "Z"),
    returnType = "Z",
)

val featureFlagPatch =
    patch(
        name = "Hook feature flag",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(featureFlagResourcePatch, settingsPatch)
        execute {

            val booleanMethod = FeatureFlagFingerprint.method

            val loc =
                booleanMethod.instructions
                    .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    .location.index
            val valueRegister =
                booleanMethod.getInstruction<OneRegisterInstruction>(loc).registerA

            val METHOD =
                """
                invoke-static {p1,v$valueRegister}, $PATCHES_DESCRIPTOR/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$valueRegister
                """.trimIndent()

            booleanMethod.addInstructions(loc + 1, METHOD)

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$FSTS_DESCRIPTOR->load()V",
            )

            enableSettings("enableFeatureFlags")
        }
    }

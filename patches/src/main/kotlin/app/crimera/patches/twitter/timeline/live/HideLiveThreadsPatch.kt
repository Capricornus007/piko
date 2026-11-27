/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.live

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

// 2026-11-27 覆核推翻先前 no-op 判斷（見 /tmp/recheck_livethreads.md）：
// Fleets/Live Threads 未被物理移除，而是改名搬遷為 Fleetline / Spaces bar。
// 舊 JsonFleetsTimelineResponse → com.x.http.spaces.FleetlineApiResponse(r0)，
// 渲染為 com.x.spaces.ui.home.o 的 Spaces bar 橫條；三方證據（jadx+dex+UI 入口）俱在。
//
// 注入點選唯一 mapper com.x.spaces.bar.q.f(ApiFleetThread,Map,Map):e——
// 它被 fetch(q.c) 與 cache(q.d) 兩條路徑共用，兩處呼叫端都內建 if-eqz null
// 檢查；讓 f() 在開關開啟時回 null 即同時清空兩條路徑的 items，不需在三個
// com.x.spaces.model.f0 建構點分別清 list（那樣會漏兩處）。
private object SpacesBarThreadMapperFingerprint : Fingerprint(
    definingClass = "Lcom/x/spaces/bar/q;",
    name = "f",
    returnType = "Lcom/x/spaces/model/e;",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Ljava/util/Map;",
                name = "get",
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lcom/x/clock/d;",
                name = "J",
            ),
        ),
)

@Suppress("unused")
val hideLiveThreadsPatch =
    patch(
        name = "Hide Live Threads",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = SpacesBarThreadMapperFingerprint.method
            val first = method.instructions.first()

            // 方法開頭插入：開關開啟 → 直接回傳 null（呼叫端 q.c/q.d 的
            // if-eqz 已有「略過此 item」語意）。v0 在原 idx 0 會被 v18
            // 還原，故此處借用 v0 存 boolean 安全。
            method.addInstructionsWithLabels(
                0,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->hideLiveThreads()Z
                move-result v0
                if-eqz v0, :piko_show
                const/4 v0, 0
                return-object v0
                """.trimIndent(),
                ExternalLabel("piko_show", first),
            )

            enableSettings("hideLiveThreads")
        }
    }

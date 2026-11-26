/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.logging.responseLogging

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

// 2026-11-26：12.46.1-prod 移除 Jackson（fasterxml 全 dex 0 命中），不存在任何
// InputStream→JSON 通用入口（16 dex 方法層掃描實錘，見 /tmp/mining_ads_logging.md）。
// 方案 A（用戶拍板保留功能）：改 hook Apollo GraphQL 的 JSON reader 構造子
// com.apollographql.apollo.api.json.c(okio.m)——每個 GraphQL 響應解析都會
// new c(source)（payments/gif/finance/xchat/subscriptions 等 adapter 均實錘）。
// okio.m = R8 混淆後的 BufferedSource（包名保留、類名混淆）。
// 覆蓋面說明：GraphQL 主 API 流量全覆蓋；非 Apollo 的少量 REST/媒體響應不在
// 此路徑（12.46.1 架構限制，功能語意由「所有 JSON」收斂為「所有 GraphQL 響應」）。
internal object ApolloJsonReaderFingerprint : Fingerprint(
    definingClass = "Lcom/apollographql/apollo/api/json/c;",
    name = "<init>",
    parameters = listOf("Lokio/m"),
)

@Suppress("unused")
val responseLoggingPatch =
    patch(
        name = "Log server response",
        description = "Log json responses received from server",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            ApolloJsonReaderFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/loggers/ResponseLogger;->saveBufferedSource(Lokio/m;)Lokio/m;
                move-result-object p1
                """.trimIndent(),
            )
            enableSettings("serverResponseLogging")

            enableSettings("serverResponseLoggingOverwriteFile")
        }
    }

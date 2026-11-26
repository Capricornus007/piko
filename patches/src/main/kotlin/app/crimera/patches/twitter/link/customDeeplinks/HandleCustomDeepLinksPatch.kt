package app.crimera.patches.twitter.link.customDeeplinks

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.AccessFlags

// 12.46.1-prod：單一 Activity 架構，UrlInterpreterActivity 已移除；
// http/https deeplink（twitter.com/x.com）改由 com.x.android.main.MainActivity
// 的 VIEW intent-filter 接收（見 base.apk AndroidManifest），故改掛其 onCreate。
// androguard 實查 classes7.dex：onCreate(Body)V 為 public final，類內唯一。
private object UrlInterpreterActivityCreateFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

val handleCustomDeepLinksPatch =
    patch(
        description = "handle custom deeplink",
    ) {
        execute {
            UrlInterpreterActivityCreateFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, $PATCHES_DESCRIPTOR/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V",
            )
        }
    }

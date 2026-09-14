package app.ahmedyarub.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {

    /**
     * Reddit for Android.
     *
     * Only versions that have been verified against a decompiled APK are listed here.
     * 2026.37.0 is the version the Reddit Pro patch was developed and verified against.
     */
    val COMPATIBILITY_REDDIT = Compatibility(
        name = "Reddit",
        packageName = "com.reddit.frontpage",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(
            AppTarget(
                version = "2026.37.0",
                minSdk = 29
            )
        )
    )
}

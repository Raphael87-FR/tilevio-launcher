package dev.tilevio.launcher

import android.content.ComponentName
import android.graphics.Bitmap

data class App(
    val componentName: ComponentName,
    val label: String,
    val icon: Bitmap,
    val banner: Bitmap? = null,
    val lastUpdateTime: Long = 0,
) {
    val key: String
        get() = componentName.flattenToString()
}

/** Installed Apps other than Tilevio itself, one per package. */
fun interface LaunchableApps {
    /** Reuses the Apps of [known] whose package and component did not change, so only changed packages load bitmaps. */
    fun load(known: List<App>): List<App>
}

fun interface AppLauncher {
    fun launch(app: App): Boolean
}

data class LauncherDependencies(
    val launchableApps: LaunchableApps,
    val appLauncher: AppLauncher,
    val appVisibilityStore: AppVisibilityStore,
    val packageChangeEvents: PackageChangeEvents,
    val defaultHome: DefaultHome,
    val wallpaperStore: WallpaperStore,
)

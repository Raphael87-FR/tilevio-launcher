package dev.tilevio.launcher

import android.content.ComponentName
import android.graphics.Bitmap

data class App(
    val componentName: ComponentName,
    val label: String,
    val icon: Bitmap,
    val banner: Bitmap? = null,
) {
    val key: String
        get() = componentName.flattenToString()
}

/** Installed Apps, one per package. */
fun interface LaunchableApps {
    fun load(): List<App>
}

fun interface AppLauncher {
    fun launch(app: App): Boolean
}

data class LauncherDependencies(
    val launchableApps: LaunchableApps,
    val ownPackageName: String,
    val appLauncher: AppLauncher,
    val appVisibilityStore: AppVisibilityStore,
    val packageChangeEvents: PackageChangeEvents,
    val defaultHome: DefaultHome,
    val wallpaperStore: WallpaperStore,
)

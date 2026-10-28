package dev.tilevio.launcher

import android.app.Application

open class TilevioApplication : Application() {
    open val launcherDependencies: LauncherDependencies by lazy {
        LauncherDependencies(
            launchableApps = AndroidLaunchableApps(
                packageManager = packageManager,
                tileWidthPx = resources.displayMetrics.widthPixels / TILE_COLUMN_COUNT,
            ),
            ownPackageName = packageName,
            appLauncher = AndroidAppLauncher(this),
            appVisibilityStore = SharedPreferencesAppVisibilityStore(this),
            packageChangeEvents = AndroidPackageChangeEvents(this),
            defaultHome = DefaultHome(AndroidDefaultHomeAdapter(this)),
            wallpaperStore = SharedPreferencesWallpaperStore(this),
        )
    }
}

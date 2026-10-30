package dev.tilevio.launcher

import android.app.Application

open class TilevioApplication : Application() {
    open val launcherDependencies: LauncherDependencies by lazy {
        LauncherDependencies(
            launchableApps = AndroidLaunchableApps(
                packageManager = packageManager,
                ownPackageName = packageName,
                tileWidthPx = TileGeometry.bannerWidthPx(resources.displayMetrics.widthPixels, resources.displayMetrics.density),
            ),
            appLauncher = AndroidAppLauncher(this),
            appVisibilityStore = SharedPreferencesAppVisibilityStore(this),
            packageChangeEvents = AndroidPackageChangeEvents(this),
            defaultHome = DefaultHome(AndroidDefaultHomeAdapter(this)),
            wallpaperStore = SharedPreferencesWallpaperStore(this),
        )
    }
}

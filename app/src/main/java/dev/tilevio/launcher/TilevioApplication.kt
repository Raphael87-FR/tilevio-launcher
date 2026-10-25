package dev.tilevio.launcher

import android.app.Application

open class TilevioApplication : Application() {
    open val launcherDependencies: LauncherDependencies by lazy {
        LauncherDependencies(
            catalog = InstalledAppCatalog(
                launchableApps = AndroidLaunchableApps(packageManager),
                ownPackageName = packageName,
            ),
            appLauncher = AndroidAppLauncher(this),
        )
    }
}

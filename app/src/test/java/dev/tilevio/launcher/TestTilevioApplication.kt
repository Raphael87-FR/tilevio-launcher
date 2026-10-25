package dev.tilevio.launcher

class TestTilevioApplication : TilevioApplication() {
    val launchableApps = FakeLaunchableApps()
    val appLauncher = FakeAppLauncher()
    var useAndroidAppLauncher = false

    override val launcherDependencies: LauncherDependencies by lazy {
        LauncherDependencies(
            catalog = InstalledAppCatalog(launchableApps, packageName),
            appLauncher = AppLauncher { app ->
                if (useAndroidAppLauncher) {
                    AndroidAppLauncher(this).launch(app)
                } else {
                    appLauncher.launch(app)
                }
            },
        )
    }

    fun reset() {
        launchableApps.apps = emptyList()
        appLauncher.launchedApps.clear()
        appLauncher.succeeds = true
        useAndroidAppLauncher = false
    }
}

class FakeLaunchableApps : LaunchableApps {
    var apps: List<App> = emptyList()

    override fun load(): List<App> = apps
}

class FakeAppLauncher : AppLauncher {
    val launchedApps = mutableListOf<App>()
    var succeeds = true

    override fun launch(app: App): Boolean {
        launchedApps += app
        return succeeds
    }
}

package dev.tilevio.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings

class TestTilevioApplication : TilevioApplication() {
    val launchableApps = FakeLaunchableApps()
    val appLauncher = FakeAppLauncher()
    val appVisibilityStore = FakeAppVisibilityStore()
    val packageChangeEvents = FakePackageChangeEvents()
    val homeRole = FakeHomeRoleAdapter()
    val homeRolePromptStore = FakeHomeRolePromptStore()
    val wallpaperStore = FakeWallpaperStore()
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
            appVisibilityStore = appVisibilityStore,
            packageChangeEvents = packageChangeEvents,
            homeRole = homeRole,
            homeRolePromptStore = homeRolePromptStore,
            wallpaperStore = wallpaperStore,
        )
    }

    fun reset() {
        launchableApps.apps = emptyList()
        appLauncher.launchedApps.clear()
        appLauncher.succeeds = true
        appVisibilityStore.reset()
        packageChangeEvents.reset()
        homeRole.isAvailable = true
        homeRole.isHeld = true
        homeRole.requestIntents.clear()
        homeRole.homeSettingsIntents.clear()
        homeRolePromptStore.hasHandledInitialRequest = false
        wallpaperStore.reset()
        useAndroidAppLauncher = false
    }
}

class FakeAppVisibilityStore : AppVisibilityStore {
    var currentProfile = "default"
    private val hiddenPackagesByProfile = mutableMapOf<String, Set<String>>()

    override val hiddenPackageNames: Set<String>
        get() = hiddenPackagesByProfile[currentProfile].orEmpty()

    override fun setHidden(packageName: String, hidden: Boolean) {
        hiddenPackagesByProfile[currentProfile] = hiddenPackageNames.toMutableSet().apply {
            if (hidden) add(packageName) else remove(packageName)
        }
    }

    fun reset() {
        currentProfile = "default"
        hiddenPackagesByProfile.clear()
    }
}

class FakePackageChangeEvents : PackageChangeEvents {
    private var onChange: (() -> Unit)? = null

    override fun register(onChange: () -> Unit): () -> Unit {
        this.onChange = onChange
        return { this.onChange = null }
    }

    fun emit() {
        onChange?.invoke()
    }

    fun reset() {
        onChange = null
    }
}

class FakeWallpaperStore : WallpaperStore {
    override var wallpaper = Wallpaper.Charcoal
    var imageToImport: Bitmap? = null
    var importedUri: Uri? = null
    var importWidth = 0
    var importHeight = 0
    var importCount = 0
    var sourceAvailable = true
    var importFailure: Exception? = null
    private var importedImage: Bitmap? = null

    override val selection: WallpaperSelection
        get() = importedImage?.let(WallpaperSelection::Image)
            ?: WallpaperSelection.Solid(wallpaper)

    override fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image {
        importCount++
        check(sourceAvailable)
        importFailure?.let { throw it }
        importedUri = uri
        importWidth = width
        importHeight = height
        val image = checkNotNull(imageToImport)
        importedImage = image
        return WallpaperSelection.Image(image)
    }

    fun reset() {
        wallpaper = Wallpaper.Charcoal
        imageToImport = null
        importedUri = null
        importWidth = 0
        importHeight = 0
        importCount = 0
        sourceAvailable = true
        importFailure = null
        importedImage = null
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

class FakeHomeRoleAdapter : HomeRoleAdapter {
    override var isAvailable = true
    override var isHeld = true
    val requestIntents = mutableListOf<Intent>()
    val homeSettingsIntents = mutableListOf<Intent>()

    override fun createRequestIntent(): Intent = Intent("dev.tilevio.launcher.test.REQUEST_HOME_ROLE")
        .also(requestIntents::add)

    override fun createHomeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
        .also(homeSettingsIntents::add)
}

class FakeHomeRolePromptStore : HomeRolePromptStore {
    override var hasHandledInitialRequest = false
}

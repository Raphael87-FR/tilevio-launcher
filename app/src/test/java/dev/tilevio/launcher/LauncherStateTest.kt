package dev.tilevio.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherStateTest {
    private val launchableApps = FakeLaunchableApps()
    private val appLauncher = FakeAppLauncher()
    private val appVisibilityStore = FakeAppVisibilityStore()
    private val packageChangeEvents = FakePackageChangeEvents()
    private val homeRole = FakeHomeRoleAdapter()
    private val homeRolePromptStore = FakeHomeRolePromptStore()
    private val wallpaperStore = FakeWallpaperStore()
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun catalogIsPresentedAlphabeticallyWithoutDuplicatesOrTilevio() {
        launchableApps.apps = listOf(
            app("Zulu", "zulu"),
            app("Mobile Alpha", "duplicate", televisionActivity = false),
            app("Bravo", "bravo"),
            app("Alpha", "duplicate", televisionActivity = true),
            app("Tilevio", OWN_PACKAGE_NAME),
        )

        val state = launcherState()

        assertEquals(listOf("Alpha", "Bravo", "Zulu"), state.visibleApps.map(App::label))
    }

    @Test
    fun hidingAndShowingAnAppMovesItBetweenVisibleAndHiddenApps() {
        launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        val state = launcherState()
        val alpha = state.apps.first()

        state.setHidden(alpha, hidden = true)

        assertEquals(listOf("Bravo"), state.visibleApps.map(App::label))
        assertEquals(listOf("Alpha"), state.hiddenApps.map(App::label))
        assertEquals(setOf("example.alpha"), appVisibilityStore.hiddenPackageNames)

        state.setHidden(alpha, hidden = false)

        assertEquals(listOf("Alpha", "Bravo"), state.visibleApps.map(App::label))
        assertEquals(emptyList<App>(), state.hiddenApps)
    }

    @Test
    fun hiddenAppStaysHiddenAfterUninstallAndReinstall() {
        launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        val state = launcherState()
        state.setHidden(state.apps.first(), hidden = true)

        launchableApps.apps = listOf(app("Bravo", "bravo"))
        packageChangeEvents.emit()

        assertEquals(listOf("Bravo"), state.apps.map(App::label))
        launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        packageChangeEvents.emit()

        assertEquals(listOf("Bravo"), state.visibleApps.map(App::label))
        assertEquals(listOf("Alpha"), state.hiddenApps.map(App::label))
    }

    @Test
    fun hiddenAppsAreReadFromTheVisibilityStore() {
        launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        appVisibilityStore.setHidden("example.bravo", hidden = true)

        val state = launcherState()

        assertEquals(listOf("Alpha"), state.visibleApps.map(App::label))
        assertEquals(listOf("Bravo"), state.hiddenApps.map(App::label))
    }

    @Test
    fun launchingAnAppOpensItWithoutMessage() {
        launchableApps.apps = listOf(app("Alpha", "alpha"))
        val state = launcherState()

        state.launch(state.apps.single())

        assertEquals(listOf("Alpha"), appLauncher.launchedApps.map(App::label))
        assertEquals(null, state.message)
    }

    @Test
    fun launchFailureShowsAMessageAndRefreshesTheCatalog() {
        launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        val state = launcherState()
        val bravo = state.apps.last()
        appLauncher.succeeds = false
        launchableApps.apps = listOf(app("Alpha", "alpha"))

        state.launch(bravo)

        assertEquals(LauncherMessage.LaunchFailure("Bravo"), state.message)
        assertEquals(listOf("Alpha"), state.apps.map(App::label))
    }

    @Test
    fun messageDisappearsAfterFourSeconds() {
        launchableApps.apps = listOf(app("Alpha", "alpha"))
        val state = launcherState()
        appLauncher.succeeds = false
        state.launch(state.apps.single())

        idleMainLooper(3_999)

        assertEquals(LauncherMessage.LaunchFailure("Alpha"), state.message)

        idleMainLooper(1)

        assertEquals(null, state.message)
    }

    @Test
    fun repeatedMessageKeepsItsInitialFourSeconds() {
        launchableApps.apps = listOf(app("Alpha", "alpha"))
        val state = launcherState()
        appLauncher.succeeds = false
        state.launch(state.apps.single())
        idleMainLooper(3_000)

        state.launch(state.apps.single())
        idleMainLooper(1_000)

        assertEquals(null, state.message)
    }

    @Test
    fun differentMessageGetsItsOwnFourSeconds() {
        launchableApps.apps = listOf(app("Alpha", "alpha"))
        val state = launcherState()
        appLauncher.succeeds = false
        state.launch(state.apps.single())
        idleMainLooper(3_000)

        state.chooseWallpaperImage { throw ActivityNotFoundException() }
        idleMainLooper(3_000)

        assertEquals(LauncherMessage.WallpaperImportFailure, state.message)
        idleMainLooper(1_000)
        assertEquals(null, state.message)
    }

    @Test
    fun wallpaperIsReadFromTheWallpaperStore() {
        wallpaperStore.wallpaper = Wallpaper.Graphite

        val state = launcherState()

        assertEquals(WallpaperSelection.Solid(Wallpaper.Graphite), state.wallpaper)
    }

    @Test
    fun selectingAWallpaperAppliesAndStoresIt() {
        val state = launcherState()

        state.selectWallpaper(Wallpaper.Slate)

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), state.wallpaper)
        assertEquals(Wallpaper.Slate, wallpaperStore.wallpaper)
    }

    @Test
    fun importingAnImageAppliesItAtTheRequestedSize() {
        val image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        wallpaperStore.imageToImport = image
        val state = launcherState()

        state.importWallpaperImage(Uri.parse("content://example/wallpaper"), width = 1920, height = 1080)

        assertEquals(WallpaperSelection.Image(image), state.wallpaper)
        assertEquals(Uri.parse("content://example/wallpaper"), wallpaperStore.importedUri)
        assertEquals(1920, wallpaperStore.importWidth)
        assertEquals(1080, wallpaperStore.importHeight)
        assertEquals(null, state.message)
    }

    @Test
    fun failedImageImportShowsAMessageAndKeepsThePreviousWallpaper() {
        wallpaperStore.wallpaper = Wallpaper.Slate
        wallpaperStore.importFailure = IOException("Copy failed")
        val state = launcherState()

        state.importWallpaperImage(Uri.parse("content://example/invalid"), width = 1920, height = 1080)

        assertEquals(LauncherMessage.WallpaperImportFailure, state.message)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), state.wallpaper)
    }

    @Test
    fun unavailableImagePickerShowsTheImportFailureMessage() {
        val state = launcherState()

        state.chooseWallpaperImage { throw ActivityNotFoundException() }

        assertEquals(LauncherMessage.WallpaperImportFailure, state.message)
    }

    @Test
    fun availableImagePickerIsOpenedWithoutMessage() {
        val state = launcherState()
        var pickerOpenings = 0

        state.chooseWallpaperImage { pickerOpenings++ }

        assertEquals(1, pickerOpenings)
        assertEquals(null, state.message)
    }

    @Test
    fun firstLaunchRequestsTheDefaultHomeOnlyOnce() {
        homeRole.isHeld = false

        assertEquals(true, launcherState().takeInitialDefaultHomeRequest())
        assertEquals(false, launcherState().takeInitialDefaultHomeRequest())
    }

    @Test
    fun firstLaunchDoesNotRequestTheDefaultHomeWhenAlreadyHeld() {
        homeRole.isHeld = true

        assertEquals(false, launcherState().takeInitialDefaultHomeRequest())
        homeRole.isHeld = false
        assertEquals(true, launcherState().takeInitialDefaultHomeRequest())
    }

    @Test
    fun defaultHomeStateIsReadAgainOnRefresh() {
        homeRole.isHeld = false
        val state = launcherState()
        assertEquals(false, state.isDefaultHome)

        homeRole.isHeld = true

        assertEquals(false, state.isDefaultHome)
        state.refreshDefaultHome()
        assertEquals(true, state.isDefaultHome)
    }

    private fun idleMainLooper(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    private fun launcherState() = LauncherState(
        dependencies = LauncherDependencies(
            catalog = InstalledAppCatalog(launchableApps, OWN_PACKAGE_NAME),
            appLauncher = appLauncher,
            appVisibilityStore = appVisibilityStore,
            packageChangeEvents = packageChangeEvents,
            homeRole = homeRole,
            homeRolePromptStore = homeRolePromptStore,
            wallpaperStore = wallpaperStore,
        ),
        scope = scope,
        ioDispatcher = Dispatchers.Unconfined,
    ).also { state -> state.watchCatalog() }

    private fun app(
        label: String,
        packageSuffix: String,
        televisionActivity: Boolean = true,
    ): App {
        val packageName = if (packageSuffix == OWN_PACKAGE_NAME) packageSuffix else "example.$packageSuffix"
        val activityName = if (televisionActivity) "TvActivity" else "MobileActivity"
        return App(
            componentName = ComponentName(packageName, "$packageName.$activityName"),
            label = label,
            icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
            isTelevisionEntry = televisionActivity,
        )
    }

    private companion object {
        const val OWN_PACKAGE_NAME = "dev.tilevio.launcher"
    }
}

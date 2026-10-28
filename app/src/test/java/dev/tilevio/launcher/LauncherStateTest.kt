package dev.tilevio.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherStateTest {
    private val launchableApps = FakeLaunchableApps()
    private val appLauncher = FakeAppLauncher()
    private val appVisibilityStore = FakeAppVisibilityStore()
    private val packageChangeEvents = FakePackageChangeEvents()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val wallpaperStore = SharedPreferencesWallpaperStore(context)
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    private val backgroundDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @After
    fun tearDown() {
        scope.cancel()
        backgroundDispatcher.close()
    }

    @Test
    fun catalogIsPresentedAlphabeticallyWithoutTilevio() {
        launchableApps.apps = listOf(
            app("Zulu", "zulu"),
            app("Bravo", "bravo"),
            app("Alpha", "alpha"),
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
        SharedPreferencesWallpaperStore(context).select(Wallpaper.Graphite)

        val state = launcherState()

        assertEquals(WallpaperSelection.Solid(Wallpaper.Graphite), state.wallpaper)
    }

    @Test
    fun wallpaperIsReadOffTheMainThread() {
        val readThreads = CopyOnWriteArrayList<Thread>()
        val state = launcherState(
            wallpaperStore = object : WallpaperStore by wallpaperStore {
                override val selection: WallpaperSelection
                    get() = wallpaperStore.selection.also { readThreads += Thread.currentThread() }
            },
            ioDispatcher = backgroundDispatcher,
        )

        waitUntil { state.wallpaper != null }

        assertEquals(WallpaperSelection.Solid(Wallpaper.Charcoal), state.wallpaper)
        assertNotEquals(Looper.getMainLooper().thread, readThreads.single())
    }

    @Test
    fun selectingAWallpaperAppliesAndStoresIt() {
        val state = launcherState()

        state.selectWallpaper(Wallpaper.Slate)

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), state.wallpaper)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), SharedPreferencesWallpaperStore(context).selection)
    }

    @Test
    fun importingAnImageAppliesItBoundedToTheRequestedSize() {
        val image = solidImage(3840, 2160, Color.RED)
        val document = registerTestImage(context, "content://example/wallpaper", image)
        val state = launcherState()

        state.importWallpaperImage(document.uri, width = 1920, height = 1080)

        val wallpaper = state.wallpaper as WallpaperSelection.Image
        assertEquals(1920, wallpaper.bitmap.width)
        assertEquals(1080, wallpaper.bitmap.height)
        assertEquals(Color.RED, wallpaper.bitmap.getPixel(960, 540))
        assertEquals(null, state.message)
    }

    @Test
    fun solidWallpaperSelectedDuringAnImageImportStaysApplied() {
        val document = registerTestImage(context, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        val state = launcherState()
        document.onOpen = { state.selectWallpaper(Wallpaper.Slate) }

        state.importWallpaperImage(document.uri, width = 1920, height = 1080)

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), state.wallpaper)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), SharedPreferencesWallpaperStore(context).selection)
    }

    @Test
    fun solidWallpaperSelectedBeforeAStoredImageIsAppliedStaysApplied() {
        val imageStored = CountDownLatch(1)
        val applyImage = CountDownLatch(1)
        val document = registerTestImage(context, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        val state = launcherState(
            wallpaperStore = object : WallpaperStore by wallpaperStore {
                override fun importImage(uri: Uri, width: Int, height: Int) =
                    wallpaperStore.importImage(uri, width, height).also {
                        imageStored.countDown()
                        check(applyImage.await(5, TimeUnit.SECONDS))
                    }
            },
            ioDispatcher = backgroundDispatcher,
        )
        waitUntil { state.wallpaper != null }

        state.importWallpaperImage(document.uri, width = 1920, height = 1080)
        check(imageStored.await(5, TimeUnit.SECONDS))
        state.selectWallpaper(Wallpaper.Slate)
        applyImage.countDown()
        (backgroundDispatcher.executor as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), state.wallpaper)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), SharedPreferencesWallpaperStore(context).selection)
    }

    @Test
    fun failedImageImportShowsAMessageAndKeepsThePreviousWallpaper() {
        wallpaperStore.select(Wallpaper.Slate)
        val document = registerInvalidImage(context, "content://example/invalid")
        val state = launcherState()

        state.importWallpaperImage(document.uri, width = 1920, height = 1080)

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

    private fun idleMainLooper(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Condition not met" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun launcherState(
        wallpaperStore: WallpaperStore = this.wallpaperStore,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = LauncherState(
        dependencies = LauncherDependencies(
            launchableApps = launchableApps,
            ownPackageName = OWN_PACKAGE_NAME,
            appLauncher = appLauncher,
            appVisibilityStore = appVisibilityStore,
            packageChangeEvents = packageChangeEvents,
            defaultHome = DefaultHome(FakeDefaultHomeAdapter()),
            wallpaperStore = wallpaperStore,
        ),
        scope = scope,
        ioDispatcher = ioDispatcher,
    ).also { state -> state.watchCatalog() }

    private fun app(label: String, packageSuffix: String): App {
        val packageName = if (packageSuffix == OWN_PACKAGE_NAME) packageSuffix else "example.$packageSuffix"
        return App(
            componentName = ComponentName(packageName, "$packageName.TvActivity"),
            label = label,
            icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
        )
    }

    private companion object {
        const val OWN_PACKAGE_NAME = "dev.tilevio.launcher"
    }
}

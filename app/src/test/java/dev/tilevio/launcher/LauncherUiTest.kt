@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tilevio.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.SystemClock
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(
    application = TestTilevioApplication::class,
    sdk = [35],
    instrumentedPackages = ["dev.tilevio.launcher"],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var testApplication: TestTilevioApplication

    @Before
    fun setUp() {
        testApplication = ApplicationProvider.getApplicationContext()
        testApplication.reset()
    }

    @Test
    @Config(qualifiers = "en")
    fun clockUsesSystemTimeFormat() {
        launchLauncherAt(hour = 13, minute = 37, second = 42)

        composeRule.onNodeWithText("13:37").assertIsDisplayed()

        Settings.System.putInt(testApplication.contentResolver, Settings.System.TIME_12_24, 12)
        launchLauncher()

        composeRule.onNodeWithText("1:37 PM").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en", shadows = [NetworkClockOneHourAhead::class])
    fun clockFollowsSystemTimeAfterManualChangeRatherThanNetworkTime() {
        launchLauncherAt(hour = 13, minute = 37, second = 42)

        composeRule.onNodeWithText("13:37").assertIsDisplayed()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        setSystemTime(hour = 20, minute = 15, second = 0)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("20:15").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun clockRefreshesAtMinuteChangesOnlyOnHome() {
        launchLauncherAt(hour = 13, minute = 37, second = 50)
        composeRule.onNodeWithText("13:37").assertIsDisplayed()

        advanceTimeBy(5_000)

        composeRule.onNodeWithText("13:37").assertIsDisplayed()
        composeRule.onNodeWithText("13:38").assertDoesNotExist()

        advanceTimeBy(5_000)

        composeRule.onNodeWithText("13:38").assertIsDisplayed()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        advanceTimeBy(60_000)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("13:39").assertIsDisplayed()
        openSettings()
        composeRule.onNodeWithText("13:39").assertDoesNotExist()

        advanceTimeBy(60_000)
        closeSettings()

        composeRule.onNodeWithText("13:40").assertIsDisplayed()
    }

    @Test
    fun launcherUsesDarkWallpaperByDefault() {
        launchLauncher()

        assertEquals(0xFF171717.toInt(), wallpaperPixel())
    }

    @Test
    fun settingsOffersFixedDarkNeutralWallpaperPalette() {
        launchLauncher()
        openSettings()

        composeRule.onNodeWithText("Wallpaper").assertIsDisplayed()
        composeRule.onNodeWithText("Charcoal").assertIsDisplayed()
        composeRule.onNodeWithText("Graphite").assertIsDisplayed()
        composeRule.onNodeWithText("Slate").assertIsDisplayed()
        composeRule.onNodeWithText("Warm Gray").assertIsDisplayed()
        composeRule.onNodeWithText("Custom color").assertDoesNotExist()
    }

    @Test
    fun selectingImageDocumentImportsAndAppliesWallpaperImmediately() {
        val document = registerTestImage(testApplication, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        launchLauncher()
        openSettings()

        val picker = openImagePicker()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        assertEquals(
            listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"),
            picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList(),
        )
        completeSuccessfulImageSelection(picker, document)

        composeRule.onNodeWithText("Choose image").assertIsSelected()
        assertEquals(Color.RED, wallpaperPixel())
    }

    @Test
    fun imageSelectionStillAppliesAfterActivityRecreationInDocumentPicker() {
        val document = registerTestImage(testApplication, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        launchLauncher()
        openSettings()
        val picker = openImagePicker()

        composeRule.activity.recreate()
        composeRule.waitForIdle()
        completeSuccessfulImageSelection(picker, document)

        assertEquals(Color.RED, wallpaperPixel())
        val openCount = document.openCount
        composeRule.activity.recreate()
        awaitWallpaperPixel(Color.RED)
        assertEquals(openCount, document.openCount)
    }

    @Test
    fun importedWallpaperSurvivesRecreationWithoutOriginalDocument() {
        val document = registerTestImage(testApplication, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        launchLauncher()
        openSettings()
        completeSuccessfulImageSelection(openImagePicker(), document)
        closeSettings()
        val openCount = document.openCount

        document.isAvailable = false
        composeRule.activity.recreate()

        awaitWallpaperPixel(Color.RED)
        assertEquals(openCount, document.openCount)
    }

    @Test
    fun invalidImageShowsErrorAndPreservesPreviousWallpaper() {
        val document = registerInvalidImage(testApplication, "content://example/invalid")
        launchLauncher()
        openSettings()
        composeRule.onNodeWithText("Slate").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        completeImageSelection(openImagePicker(), document.uri)

        assertWallpaperImportFailure()
        composeRule.onNodeWithText("Slate").assertIsSelected()
        assertEquals(0xFF263238.toInt(), wallpaperPixel())
    }

    @Test
    fun failedSecondImportShowsErrorAndPreservesPreviousImage() {
        val first = registerTestImage(testApplication, "content://example/first", solidImage(4, 4, Color.RED))
        val second = registerTestImage(testApplication, "content://example/second", solidImage(4, 4, Color.RED))
        second.isAvailable = false
        launchLauncher()
        openSettings()
        completeSuccessfulImageSelection(openImagePicker(), first)
        assertEquals(Color.RED, wallpaperPixel())

        completeImageSelection(openImagePicker(), second.uri)

        assertWallpaperImportFailure()
        composeRule.onNodeWithText("Choose image").assertIsSelected()
        assertEquals(Color.RED, wallpaperPixel())
    }

    @Test
    fun selectingSolidWallpaperAfterImageReplacesItAcrossRecreation() {
        val document = registerTestImage(testApplication, "content://example/wallpaper", solidImage(4, 4, Color.RED))
        launchLauncher()
        openSettings()
        completeSuccessfulImageSelection(openImagePicker(), document)

        composeRule.onNodeWithText("Graphite").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        closeSettings()
        composeRule.activity.recreate()

        awaitWallpaperPixel(0xFF292929.toInt())
    }

    @Test
    fun importedWallpaperFillsScreenWithPreservedAspectRatioAndCenterCrop() {
        val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                image.setPixel(
                    x,
                    y,
                    when {
                        x < 100 -> Color.RED
                        x >= 300 -> Color.BLUE
                        else -> Color.GREEN
                    },
                )
            }
        }
        val document = registerTestImage(testApplication, "content://example/bands", image)
        launchLauncher()
        openSettings()
        completeSuccessfulImageSelection(openImagePicker(), document, expectedPixel = Color.GREEN)
        closeSettings()

        val inset = composeRule.activity.window.decorView.width / 20
        assertEquals(Color.GREEN, wallpaperPixel(x = inset))
        assertEquals(
            Color.GREEN,
            wallpaperPixel(x = composeRule.activity.window.decorView.width - 1 - inset),
        )
    }

    @Test
    fun selectingWallpaperAppliesImmediatelyWithoutSave() {
        launchLauncher()
        openSettings()

        composeRule.onNodeWithText("Slate").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText("Slate").assertIsSelected()
        assertEquals(0xFF263238.toInt(), wallpaperPixel())
        composeRule.onNodeWithText("Save").assertDoesNotExist()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals(0xFF263238.toInt(), wallpaperPixel())
    }

    @Test
    fun selectedWallpaperSurvivesActivityRecreation() {
        launchLauncher()
        openSettings()
        composeRule.onNodeWithText("Warm Gray").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.activity.recreate()

        awaitWallpaperPixel(0xFF2D2A26.toInt())
    }

    @Test
    fun packageManagerLoadsOnlyTheTvEntryWithItsBannerBoundedToTheTile() {
        val packageName = "example.banner"
        val iconResource = 1001
        val bannerResource = 1002
        val icon = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
        }
        val banner = Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val packageManager = testApplication.packageManager
        val shadowPackageManager = shadowOf(packageManager)
        shadowPackageManager.installPackage(PackageInfo().apply { this.packageName = packageName })
        shadowPackageManager.addDrawableResolution(
            packageName,
            iconResource,
            BitmapDrawable(testApplication.resources, icon),
        )
        shadowPackageManager.addDrawableResolution(
            packageName,
            bannerResource,
            BitmapDrawable(testApplication.resources, banner),
        )

        fun entry(activityName: String, label: String, banner: Int = 0) = ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.$activityName"
                this.icon = iconResource
                this.banner = banner
            }
        }

        shadowPackageManager.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            entry("TvActivity", "TV Alpha", bannerResource),
        )
        shadowPackageManager.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            entry("MobileActivity", "Mobile Alpha"),
        )

        val app = AndroidLaunchableApps(packageManager, testApplication.packageName, tileWidthPx = 320).load(emptyList())
            .single { app -> app.componentName.packageName == packageName }

        assertEquals(ComponentName(packageName, "$packageName.TvActivity"), app.componentName)
        assertEquals("TV Alpha", app.label)
        assertEquals(64, app.icon.width)
        assertEquals(32, app.icon.height)
        assertEquals(Color.YELLOW, app.icon.getPixel(0, 0))
        assertEquals(320, app.banner?.width)
        assertEquals(180, app.banner?.height)
        assertEquals(Color.RED, app.banner?.getPixel(0, 0))
    }

    @Test
    fun firstLaunchRequestsAvailableDefaultHomeOnlyOnce() {
        testApplication.defaultHomeAdapter.isHeld = false

        launchLauncher()
        composeRule.activity.recreate()
        composeRule.waitForIdle()

        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
    }

    @Test
    fun refusingDefaultHomeRequestDoesNotPromptOnLaterLaunches() {
        testApplication.defaultHomeAdapter.isHeld = false
        launchLauncher()
        val request = testApplication.defaultHomeAdapter.requestIntents.single()

        shadowOf(composeRule.activity).receiveResult(
            request,
            android.app.Activity.RESULT_CANCELED,
            null,
        )
        composeRule.activity.recreate()
        composeRule.waitForIdle()

        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
    }

    @Test
    fun firstLaunchFallsBackToAndroidHomeSettingsWhenRoleIsUnavailable() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.isAvailable = false

        launchLauncher()

        assertEquals(0, testApplication.defaultHomeAdapter.requestIntents.size)
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            testApplication.defaultHomeAdapter.homeSettingsIntents.single().action,
        )
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            shadowOf(testApplication).nextStartedActivity.action,
        )
    }

    @Test
    fun acceptingDefaultHomeRequestUpdatesSettingsWithoutPromptingAgain() {
        testApplication.defaultHomeAdapter.isHeld = false
        launchLauncher()
        val request = testApplication.defaultHomeAdapter.requestIntents.single()
        testApplication.defaultHomeAdapter.isHeld = true

        shadowOf(composeRule.activity).receiveResult(
            request,
            android.app.Activity.RESULT_OK,
            null,
        )
        openSettings()

        composeRule.onNodeWithText("Tilevio is your default home screen.").assertIsDisplayed()
        composeRule.activity.recreate()
        composeRule.waitForIdle()
        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
    }

    @Test
    fun returningToTheLauncherRefreshesTheDefaultHomeState() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        testApplication.defaultHomeAdapter.isHeld = true
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithText("Tilevio is your default home screen.").assertIsDisplayed()
    }

    @Test
    fun settingsDoesNotShowActivationForAFrameAfterTheRequestIsAccepted() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()
        openSettings()
        activateDefaultHome()
        val request = testApplication.defaultHomeAdapter.requestIntents.single()
        composeRule.mainClock.autoAdvance = false
        testApplication.defaultHomeAdapter.isHeld = true

        shadowOf(composeRule.activity).receiveResult(
            request,
            android.app.Activity.RESULT_OK,
            null,
        )
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Set Tilevio as default home").assertDoesNotExist()
    }

    @Test
    fun settingsShowsDefaultHomeActivationAndVersion() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()

        openSettings()

        composeRule.onNodeWithText("Set Tilevio as default home").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }

    @Test
    fun settingsCanRequestDefaultHomeAfterInitialRefusal() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()
        openSettings()

        activateDefaultHome()

        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
    }

    @Test
    fun settingsFallsBackToAndroidHomeSettingsWhenRoleIsUnavailable() {
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.isAvailable = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()
        openSettings()

        activateDefaultHome()

        assertEquals(0, testApplication.defaultHomeAdapter.requestIntents.size)
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            testApplication.defaultHomeAdapter.homeSettingsIntents.single().action,
        )
    }

    @Test
    fun emptyCatalogShowsAnExplanatoryState() {
        launchLauncher()

        composeRule.onNodeWithText(
            "No apps available to open. Install an app and return to Tilevio.",
        ).assertIsDisplayed()
    }

    @Test
    fun backLeavesSettingsAndRestoresTheFocusedApp() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
        openSettings()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(false, composeRule.activity.isFinishing)
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
        composeRule.onNodeWithText("Set Tilevio as default home").assertDoesNotExist()
    }

    @Test
    fun backOnHomeKeepsTheLauncherVisibleAndActive() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        launchLauncher()
        val activity = composeRule.activity

        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(false, activity.isFinishing)
        assertEquals(false, activity.isDestroyed)
        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun clockPrecedesSettingsWithoutJoiningRemoteNavigation() {
        launchLauncherAt(hour = 13, minute = 37, second = 42)
        val clockBounds = composeRule.onNodeWithText("13:37").fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithContentDescription("Settings")
        val settingsBounds = settings.fetchSemanticsNode().boundsInRoot

        assertEquals(true, clockBounds.left < settingsBounds.left)
        composeRule.onNodeWithText("App").assertDoesNotExist()
        settings.assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        settings.assertIsFocused()
    }

    @Test
    fun settingsGearHasTransparentRestingContainerAndVisibleFocus() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        launchLauncher()

        assertEquals(0xFF171717.toInt(), tilePixel("Settings", xFraction = 0.5f, yFraction = 0.02f))

        composeRule.onNodeWithContentDescription("Settings").requestFocus().assertIsFocused()

        assertEquals(Color.WHITE, tilePixel("Settings", xFraction = 0.5f, yFraction = 0.02f))
    }

    @Test
    fun settingsGearExposesAccessibleLabelWithoutVisibleText() {
        launchLauncher()

        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().assertIsFocused()
        composeRule.onAllNodesWithText("Settings").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun settingsGearDoesNotOverlapAnyAppTile() {
        testApplication.launchableApps.apps = (1..12).map { index ->
            app("App $index", "app$index")
        }
        launchLauncher()
        val settingsBounds = composeRule.onNodeWithContentDescription("Settings")
            .fetchSemanticsNode().boundsInRoot

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .filterNot { node ->
                node.config[SemanticsProperties.ContentDescription].single() == "Settings"
            }
            .forEach { node ->
                val label = node.config[SemanticsProperties.ContentDescription].single()
                assertEquals("Settings overlaps $label", false, settingsBounds.overlaps(node.boundsInRoot))
            }
    }

    @Test
    fun repeatedHomeIntentsReuseLauncherAndReturnFromSettings() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        testApplication.defaultHomeAdapter.isHeld = false
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        openSettings()
        val activity = composeRule.activity
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        composeRule.runOnUiThread {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.callActivityOnNewIntent(activity, homeIntent)
            instrumentation.callActivityOnNewIntent(activity, homeIntent)
        }
        composeRule.waitForIdle()

        assertEquals(activity, composeRule.activity)
        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
    }

    @Test
    fun homeActivityIsSingleTaskAndExcludedFromRecents() {
        val activityInfo = testApplication.packageManager.getActivityInfo(
            ComponentName(testApplication, MainActivity::class.java),
            0,
        )

        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
        assertEquals(
            android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS,
            activityInfo.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS,
        )
    }

    @Test
    fun coldLaunchFocusesTheFirstApp() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))

        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
    }

    @Test
    fun remoteInputOpensSettingsAndActivatesDefaultHome() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("Settings").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(1, testApplication.defaultHomeAdapter.requestIntents.size)
    }

    @Test
    fun remoteInputLeavesSettingsButtonAndReturnsToApp() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("Settings").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
    }

    @Test
    fun remoteInputMovesFocusAndOpensTheFocusedApp() {
        testApplication.launchableApps.apps = listOf(
            app("Alpha", "alpha"),
            app("Bravo", "bravo"),
        )

        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(
            ComponentName("example.bravo", "example.bravo.TvActivity"),
            testApplication.appLauncher.launchedApps.single().componentName,
        )
    }

    @Test
    fun accessibilityClickOpensTile() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            ComponentName("example.alpha", "example.alpha.TvActivity"),
            testApplication.appLauncher.launchedApps.single().componentName,
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun focusedWhiteBannerKeepsAVisibleContrastingEdge() {
        val banner = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha").copy(banner = banner))
        launchLauncher()
        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()

        assertEquals(Color.WHITE, tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.02f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.04f))
    }

    @Test
    fun mobileAppLaunchUsesTheRealAndroidAdapter() {
        testApplication.launchableApps.apps = listOf(
            app("Mobile", "mobile", televisionActivity = false),
        )
        testApplication.useAndroidAppLauncher = true
        launchLauncher()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        val launchedIntent = shadowOf(testApplication).nextStartedActivity
        assertEquals(
            ComponentName("example.mobile", "example.mobile.MobileActivity"),
            launchedIntent.component,
        )
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            launchedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
        )
    }

    @Test
    fun launchFailureRefreshesTheCatalogAndKeepsValidFocus() {
        testApplication.launchableApps.apps = listOf(
            app("Alpha", "alpha"),
            app("Bravo", "bravo"),
            app("Charlie", "charlie"),
        )
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
        testApplication.appLauncher.succeeds = false
        testApplication.launchableApps.apps = listOf(
            app("Alpha", "alpha"),
            app("Charlie", "charlie"),
        )

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText(
            "Couldn't open Bravo. The app list was refreshed.",
        ).assertIsDisplayed()
        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription("Bravo").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithContentDescription("Charlie").assertIsFocused()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithText(
            "Couldn't open Bravo. The app list was refreshed.",
        ).assertDoesNotExist()
    }

    @Test
    fun settingsListsEveryAppWithItsShownState() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()

        openSettings()

        composeRule.onNodeWithText("Apps").assertIsDisplayed()
        focusAppInSettings("Alpha")
        composeRule.onNodeWithText("Alpha").assertIsDisplayed().assertIsNotSelected()
        focusAppInSettings("Bravo")
        composeRule.onNodeWithText("Bravo").assertIsDisplayed().assertIsNotSelected()
        composeRule.onAllNodesWithText("Shown").assertCountEquals(2)
    }

    @Test
    fun hidingAndShowingAppsUpdatesTheGridWithoutSaving() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()
        openSettings()

        changeAppVisibility("Alpha")

        composeRule.onNodeWithText("Alpha").assertIsSelected()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
        closeSettings()
        composeRule.onNodeWithContentDescription("Alpha").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
        openSettings()
        changeAppVisibility("Alpha")
        composeRule.onNodeWithText("Alpha").assertIsNotSelected()
        closeSettings()

        assertEquals(listOf("Alpha", "Bravo"), visibleAppNames())
        composeRule.onNodeWithContentDescription("Bravo").assertIsFocused()
    }

    @Test
    fun hiddenAppSurvivesActivityRecreation() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()
        openSettings()
        changeAppVisibility("Alpha")
        closeSettings()

        composeRule.activity.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Alpha").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Bravo").assertIsDisplayed()
        openSettings()
        focusAppInSettings("Alpha")
        composeRule.onNodeWithText("Alpha").assertIsSelected()
    }

    @Test
    fun hidingFocusedAppsUsesTheNearestAvailableTile() {
        testApplication.launchableApps.apps = listOf(
            app("Alpha", "alpha"),
            app("Bravo", "bravo"),
            app("Charlie", "charlie"),
        )
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        openSettings()

        changeAppVisibility("Bravo")
        closeSettings()

        composeRule.onNodeWithContentDescription("Charlie").assertIsFocused()
        openSettings()
        changeAppVisibility("Charlie")
        closeSettings()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        openSettings()
        changeAppVisibility("Bravo")
        closeSettings()

        assertEquals(listOf("Alpha", "Bravo"), visibleAppNames())
        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
    }

    @Test
    fun allHiddenStateKeepsSettingsAccessibleForRecovery() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()
        openSettings()
        changeAppVisibility("Alpha")
        changeAppVisibility("Bravo")

        closeSettings()

        composeRule.onNodeWithText(
            "All Apps are hidden. Open Settings to show an App again.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsFocused()
        openSettings()
        changeAppVisibility("Alpha")
        closeSettings()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        composeRule.onNodeWithText(
            "All Apps are hidden. Open Settings to show an App again.",
        ).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun englishLocaleUsesEnglishResources() {
        launchLauncher()

        composeRule.onNodeWithText(
            "No apps available to open. Install an app and return to Tilevio.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "es")
    fun unsupportedLocaleFallsBackToEnglish() {
        launchLauncher()

        composeRule.onNodeWithText(
            "No apps available to open. Install an app and return to Tilevio.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsFocused()
        composeRule.onNodeWithContentDescription("Paramètres").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchLocaleLocalizesSettingsAndErrors() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        testApplication.defaultHomeAdapter.isHeld = false
        testApplication.defaultHomeAdapter.hasHandledInitialRequest = true
        launchLauncher()

        composeRule.onNodeWithContentDescription("Paramètres").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Fond d’écran").assertIsDisplayed()
        composeRule.onNodeWithText("Anthracite").assertExists()
        composeRule.onNodeWithText("Graphite").assertExists()
        composeRule.onNodeWithText("Ardoise").assertExists()
        composeRule.onNodeWithText("Gris chaud").assertExists()
        composeRule.onNodeWithText("Définir Tilevio comme écran d’accueil par défaut").assertExists()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithText("Choisir une image").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        completeImageSelection(
            shadowOf(composeRule.activity).nextStartedActivity,
            registerInvalidImage(testApplication, "content://example/invalid").uri,
        )
        assertWallpaperImportFailure(
            "Impossible d’utiliser cette image. Le fond d’écran précédent a été conservé.",
        )
        focusAppInSettings("Alpha")
        composeRule.onNodeWithText("Applications").assertIsDisplayed()
        composeRule.onNodeWithText("Visible").assertIsDisplayed()
        closeSettings()

        testApplication.appLauncher.succeeds = false
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText(
            "Impossible d’ouvrir Alpha. La liste des applications a été actualisée.",
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchLocaleLocalizesEmptyStatesAndKeepsSettingsAccessible() {
        launchLauncher()
        composeRule.onNodeWithText(
            "Aucune application à ouvrir. Installez une application, puis revenez dans Tilevio.",
        ).assertIsDisplayed()

        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))
        composeRule.runOnUiThread { testApplication.packageChangeEvents.emit() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Paramètres").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        changeAppVisibility("Alpha")
        closeSettings()

        composeRule.onNodeWithText(
            "Toutes les applications sont masquées. Ouvrez les paramètres pour en afficher une à nouveau.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Paramètres").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun appWithBannerFillsARoundedSixteenByNineTile() {
        val banner = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha").copy(banner = banner))

        launchLauncher()

        val size = composeRule.onNodeWithContentDescription("Alpha")
            .fetchSemanticsNode().size
        assertEquals(16f / 9f, size.width.toFloat() / size.height, 0.01f)
        assertEquals(Color.RED, tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.5f))
        assertEquals(Color.WHITE, tilePixel("Alpha", xFraction = 0.5f, yFraction = 0f))

        composeRule.onNodeWithContentDescription("Settings").requestFocus()

        assertEquals(Color.RED, tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.02f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.01f, yFraction = 0.01f))
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun nonNativeBannerKeepsItsAspectRatioWithoutCropping() {
        val banner = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (x in 20 until 40) {
                for (y in 0 until 20) {
                    setPixel(x, y, Color.BLUE)
                }
            }
        }
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha").copy(banner = banner))

        launchLauncher()
        composeRule.onNodeWithContentDescription("Settings").requestFocus()

        assertEquals(Color.RED, tilePixel("Alpha", xFraction = 0.25f, yFraction = 0.5f))
        assertEquals(Color.BLUE, tilePixel("Alpha", xFraction = 0.75f, yFraction = 0.5f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.02f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.98f))
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun appWithoutBannerCentersItsOriginalIconOnTransparentSpace() {
        val icon = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha").copy(icon = icon))

        launchLauncher()
        composeRule.onNodeWithContentDescription("Settings").requestFocus()

        assertEquals(Color.GREEN, tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.5f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.05f, yFraction = 0.5f))
        assertEquals(0xFF171717.toInt(), tilePixel("Alpha", xFraction = 0.5f, yFraction = 0.02f))
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun nineAppsUseACenteredFiveColumnGridWithRegularGeometry() {
        testApplication.launchableApps.apps = (1..9).map { index ->
            app("App $index", "app$index")
        }
        launchLauncher()

        val tiles = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        ).fetchSemanticsNodes()
            .filterNot { node ->
                node.config[SemanticsProperties.ContentDescription].single() == "Settings"
            }
            .associate { node ->
                node.config[SemanticsProperties.ContentDescription].single() to node.boundsInRoot
            }
        val rows = tiles.values.groupBy { bounds -> bounds.top.toInt() }.values
            .sortedBy { row -> row.first().top }
        assertEquals(listOf(5, 4), rows.map { row -> row.size })
        val firstRow = rows[0].sortedBy { bounds -> bounds.left }
        val secondRow = rows[1].sortedBy { bounds -> bounds.left }
        val density = composeRule.activity.resources.displayMetrics.density
        val gutter = 24 * density
        val margin = 32 * density
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val expectedTileWidth = (rootBounds.width - 2 * margin - 4 * gutter) / 5

        tiles.forEach { (label, bounds) ->
            assertEquals("$label width", expectedTileWidth, bounds.width, 1f)
            assertEquals("$label ratio", 16f / 9f, bounds.width / bounds.height, 0.01f)
        }
        firstRow.zipWithNext().forEach { (left, right) ->
            assertEquals("Horizontal gutter", gutter, right.left - left.right, 1f)
        }
        assertEquals("Vertical gutter", gutter, secondRow.first().top - firstRow.first().bottom, 1f)
        secondRow.indices.forEach { index ->
            assertEquals("Incomplete-row alignment", firstRow[index].left, secondRow[index].left, 1f)
        }
        assertEquals("Left margin", margin, firstRow.first().left, 1f)
        assertEquals("Right margin", margin, rootBounds.right - firstRow.last().right, 1f)

        val settingsBounds = composeRule.onNodeWithContentDescription("Settings")
            .fetchSemanticsNode().boundsInRoot
        val bodyTop = settingsBounds.bottom + 24 * density
        val topSpace = firstRow.first().top - bodyTop
        val bottomSpace = rootBounds.bottom - secondRow.first().bottom
        assertEquals("Vertical centering", topSpace, bottomSpace, 1f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun additionalRowsScrollWithoutChangingTileDimensions() {
        testApplication.launchableApps.apps = (1..25).map { index ->
            app("App ${index.toString().padStart(2, '0')}", "app$index")
        }
        launchLauncher()
        val initialBounds = composeRule.onNodeWithContentDescription("App 01")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("App 21").assertDoesNotExist()

        repeat(4) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }

        composeRule.onNodeWithContentDescription("App 21").assertIsDisplayed().assertIsFocused()
        val scrolledBounds = composeRule.onNodeWithContentDescription("App 21")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(initialBounds.width, scrolledBounds.width, 1f)
        assertEquals(initialBounds.height, scrolledBounds.height, 1f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp")
    fun remoteNavigationUsesAndActivatesTheIncompleteFinalRow() {
        testApplication.launchableApps.apps = (1..9).map { index ->
            app("App $index", "app$index")
        }
        launchLauncher()
        repeat(3) {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        }
        composeRule.onNodeWithContentDescription("App 4").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("App 9").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithContentDescription("App 8").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("App 9").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("App 4").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(
            ComponentName("example.app9", "example.app9.TvActivity"),
            testApplication.appLauncher.launchedApps.single().componentName,
        )
    }

    @Test
    fun tilesExposeAccessibleAppNamesWithoutVisibleText() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription("Bravo").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText("Bravo").assertDoesNotExist()
    }

    private fun changeAppVisibility(label: String) {
        focusAppInSettings(label)
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun focusAppInSettings(label: String) {
        repeat(20) {
            if (composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(label).requestFocus()
                return
            }
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        composeRule.onNodeWithText(label).requestFocus()
    }

    private fun closeSettings() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun advanceTimeBy(milliseconds: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(milliseconds))
        composeRule.mainClock.advanceTimeBy(milliseconds)
        composeRule.waitForIdle()
    }

    private fun setSystemTime(hour: Int, minute: Int, second: Int) {
        // Robolectric serves System.currentTimeMillis() from uptime in instrumented app code only.
        val currentTime = SystemClock.uptimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        ShadowSystemClock.advanceBy(Duration.ofMillis(calendar.timeInMillis - currentTime))
    }

    private fun launchLauncherAt(hour: Int, minute: Int, second: Int) {
        setSystemTime(hour, minute, second)
        Settings.System.putInt(testApplication.contentResolver, Settings.System.TIME_12_24, 24)
        launchLauncher()
    }

    private fun launchLauncher() {
        composeRule.activity.recreate()
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Settings").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }

    private fun activateDefaultHome() {
        composeRule.onNodeWithText("Set Tilevio as default home").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun openImagePicker(): Intent {
        composeRule.onNodeWithText("Choose image").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        return shadowOf(composeRule.activity).nextStartedActivity
    }

    private fun completeImageSelection(picker: Intent, uri: Uri) {
        shadowOf(composeRule.activity).receiveResult(
            picker,
            android.app.Activity.RESULT_OK,
            Intent().setData(uri),
        )
        composeRule.waitForIdle()
    }

    private fun completeSuccessfulImageSelection(
        picker: Intent,
        document: TestDocument,
        expectedPixel: Int = Color.RED,
    ) {
        completeImageSelection(picker, document.uri)
        awaitWallpaperPixel(expectedPixel)
    }

    private fun awaitWallpaperPixel(expectedPixel: Int) {
        composeRule.waitUntil {
            runCatching { wallpaperPixel() == expectedPixel }.getOrDefault(false)
        }
    }


    private fun assertWallpaperImportFailure(
        message: String = "Couldn't use that image. Your previous Wallpaper was kept.",
    ) {
        composeRule.waitUntil {
            runCatching {
                composeRule.onNodeWithText(message).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun wallpaperPixel(x: Int = 0, y: Int = 0): Int = composeRule.runOnUiThread {
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        bitmap.getPixel(x, y)
    }

    private fun tilePixel(label: String, xFraction: Float, yFraction: Float): Int =
        composeRule.runOnUiThread {
            val bounds = composeRule.onNodeWithContentDescription(label)
                .fetchSemanticsNode().boundsInRoot
            val view = composeRule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            bitmap.getPixel(
                (bounds.left + bounds.width * xFraction).toInt(),
                (bounds.top + bounds.height * yFraction).toInt(),
            )
        }

    private fun visibleAppNames(): List<String> = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        .fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.ContentDescription].single() }
        .filterNot { label -> label == "Settings" || label == "Paramètres" }

    private fun app(
        label: String,
        packageSuffix: String,
        televisionActivity: Boolean = true,
    ): App {
        val packageName = if (packageSuffix == testApplication.packageName) {
            packageSuffix
        } else {
            "example.$packageSuffix"
        }
        val activityName = if (televisionActivity) "TvActivity" else "MobileActivity"
        return App(
            componentName = ComponentName(packageName, "$packageName.$activityName"),
            label = label,
            icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
        )
    }
}

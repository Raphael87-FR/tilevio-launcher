@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tilevio.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = TestTilevioApplication::class, sdk = [35])
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
    fun catalogIsPresentedAlphabeticallyWithoutDuplicatesOrTilevio() {
        testApplication.launchableApps.apps = listOf(
            app("Zulu", "zulu"),
            app("Mobile Alpha", "duplicate", televisionActivity = false),
            app("Bravo", "bravo"),
            app("Alpha", "duplicate", televisionActivity = true),
            app("Tilevio", testApplication.packageName),
        )

        launchLauncher()

        assertEquals(listOf("Alpha", "Bravo", "Zulu"), visibleAppNames())
    }

    @Test
    fun firstLaunchRequestsAvailableHomeRoleOnlyOnce() {
        testApplication.homeRole.isHeld = false

        launchLauncher()
        composeRule.activity.recreate()
        composeRule.waitForIdle()

        assertEquals(1, testApplication.homeRole.requestIntents.size)
    }

    @Test
    fun refusingHomeRoleRequestDoesNotPromptOnLaterLaunches() {
        testApplication.homeRole.isHeld = false
        launchLauncher()
        val request = testApplication.homeRole.requestIntents.single()

        shadowOf(composeRule.activity).receiveResult(
            request,
            android.app.Activity.RESULT_CANCELED,
            null,
        )
        composeRule.activity.recreate()
        composeRule.waitForIdle()

        assertEquals(1, testApplication.homeRole.requestIntents.size)
    }

    @Test
    fun firstLaunchFallsBackToAndroidHomeSettingsWhenRoleIsUnavailable() {
        testApplication.homeRole.isHeld = false
        testApplication.homeRole.isAvailable = false

        launchLauncher()

        assertEquals(0, testApplication.homeRole.requestIntents.size)
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            testApplication.homeRole.homeSettingsIntents.single().action,
        )
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            shadowOf(testApplication).nextStartedActivity.action,
        )
    }

    @Test
    fun acceptingHomeRoleRequestUpdatesSettingsWithoutPromptingAgain() {
        testApplication.homeRole.isHeld = false
        launchLauncher()
        val request = testApplication.homeRole.requestIntents.single()
        testApplication.homeRole.isHeld = true

        shadowOf(composeRule.activity).receiveResult(
            request,
            android.app.Activity.RESULT_OK,
            null,
        )
        openSettings()

        composeRule.onNodeWithText("Tilevio is your default home screen.").assertIsDisplayed()
        composeRule.activity.recreate()
        composeRule.waitForIdle()
        assertEquals(1, testApplication.homeRole.requestIntents.size)
    }

    @Test
    fun settingsShowsDefaultHomeActivationAndVersion() {
        testApplication.homeRole.isHeld = false
        testApplication.homeRolePromptStore.hasHandledInitialRequest = true
        launchLauncher()

        openSettings()

        composeRule.onNodeWithText("Set Tilevio as default home").assertIsDisplayed()
        composeRule.onNodeWithText("Version 0.1.0").assertIsDisplayed()
    }

    @Test
    fun settingsCanRequestHomeRoleAfterInitialRefusal() {
        testApplication.homeRole.isHeld = false
        testApplication.homeRolePromptStore.hasHandledInitialRequest = true
        launchLauncher()
        openSettings()

        activateDefaultHome()

        assertEquals(1, testApplication.homeRole.requestIntents.size)
    }

    @Test
    fun settingsFallsBackToAndroidHomeSettingsWhenRoleIsUnavailable() {
        testApplication.homeRole.isHeld = false
        testApplication.homeRole.isAvailable = false
        testApplication.homeRolePromptStore.hasHandledInitialRequest = true
        launchLauncher()
        openSettings()

        activateDefaultHome()

        assertEquals(0, testApplication.homeRole.requestIntents.size)
        assertEquals(
            Settings.ACTION_HOME_SETTINGS,
            testApplication.homeRole.homeSettingsIntents.single().action,
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
        composeRule.onNodeWithText("Version 0.1.0").assertDoesNotExist()
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
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun repeatedHomeIntentsReuseLauncherAndReturnFromSettings() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"), app("Bravo", "bravo"))
        testApplication.homeRole.isHeld = false
        launchLauncher()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        openSettings()
        val activity = composeRule.activity
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        composeRule.runOnUiThread {
            activity.onNewIntent(homeIntent)
            activity.onNewIntent(homeIntent)
        }
        composeRule.waitForIdle()

        assertEquals(activity, composeRule.activity)
        assertEquals(1, testApplication.homeRole.requestIntents.size)
        composeRule.onNodeWithText("Version 0.1.0").assertDoesNotExist()
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
        testApplication.homeRole.isHeld = false
        testApplication.homeRolePromptStore.hasHandledInitialRequest = true
        launchLauncher()

        composeRule.onNodeWithContentDescription("Alpha").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Settings").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(1, testApplication.homeRole.requestIntents.size)
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

    private fun launchLauncher() {
        composeRule.activity.recreate()
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.onNodeWithText("Settings").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Version 0.1.0").assertIsDisplayed()
    }

    private fun activateDefaultHome() {
        composeRule.onNodeWithText("Set Tilevio as default home").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    }

    private fun visibleAppNames(): List<String> = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        .fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.ContentDescription].single() }

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
            isTelevisionEntry = televisionActivity,
        )
    }
}

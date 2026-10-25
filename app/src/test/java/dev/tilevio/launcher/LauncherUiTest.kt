@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tilevio.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
    fun emptyCatalogShowsAnExplanatoryState() {
        launchLauncher()

        composeRule.onNodeWithText(
            "No apps available to open. Install an app and return to Tilevio.",
        ).assertIsDisplayed()
    }

    @Test
    fun coldLaunchFocusesTheFirstApp() {
        testApplication.launchableApps.apps = listOf(app("Alpha", "alpha"))

        launchLauncher()

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
        composeRule.onNodeWithContentDescription("Bravo").assertDoesNotExist()
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

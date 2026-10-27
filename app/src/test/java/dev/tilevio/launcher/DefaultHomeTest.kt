package dev.tilevio.launcher

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultHomeTest {
    private val adapter = FakeDefaultHomeAdapter()

    @Test
    fun firstLaunchRequestsTheDefaultHomeOnlyOnce() {
        adapter.isHeld = false

        assertTrue(DefaultHome(adapter).takeInitialActivation() is DefaultHomeActivation.Request)
        assertNull(DefaultHome(adapter).takeInitialActivation())
    }

    @Test
    fun firstLaunchDoesNotRequestTheDefaultHomeWhenAlreadyHeld() {
        adapter.isHeld = true

        assertNull(DefaultHome(adapter).takeInitialActivation())
        adapter.isHeld = false
        assertNotNull(DefaultHome(adapter).takeInitialActivation())
    }

    @Test
    fun activationUsesTheRoleRequestWhenTheRoleIsAvailable() {
        adapter.isAvailable = true

        val activation = DefaultHome(adapter).activation()

        assertTrue(activation is DefaultHomeActivation.Request)
        assertEquals(adapter.requestIntents.single(), activation.intent)
        assertEquals(0, adapter.homeSettingsIntents.size)
    }

    @Test
    fun activationFallsBackToAndroidHomeSettingsWhenTheRoleIsUnavailable() {
        adapter.isAvailable = false

        val activation = DefaultHome(adapter).activation()

        assertTrue(activation is DefaultHomeActivation.HomeSettings)
        assertEquals(Settings.ACTION_HOME_SETTINGS, activation.intent.action)
        assertEquals(0, adapter.requestIntents.size)
    }

    @Test
    fun firstLaunchFallsBackToAndroidHomeSettingsWhenTheRoleIsUnavailable() {
        adapter.isHeld = false
        adapter.isAvailable = false

        assertTrue(DefaultHome(adapter).takeInitialActivation() is DefaultHomeActivation.HomeSettings)
    }

    @Test
    fun activeStateIsReadAgainOnRefresh() {
        adapter.isHeld = false
        val defaultHome = DefaultHome(adapter)
        assertFalse(defaultHome.isActive)

        adapter.isHeld = true

        assertFalse(defaultHome.isActive)
        defaultHome.refresh()
        assertTrue(defaultHome.isActive)
    }
}

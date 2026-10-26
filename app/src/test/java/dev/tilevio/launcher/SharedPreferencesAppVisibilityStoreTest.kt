package dev.tilevio.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAppVisibilityStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app-visibility", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshStoreRestoresHiddenPackages() {
        val store = SharedPreferencesAppVisibilityStore(context)
        store.setHidden("example.alpha", true)
        store.setHidden("example.bravo", true)
        store.setHidden("example.alpha", false)

        val restoredStore = SharedPreferencesAppVisibilityStore(context)

        assertEquals(setOf("example.bravo"), restoredStore.hiddenPackageNames)
    }

    @Test
    fun storeRetainsHiddenPackageWhileAppIsAbsent() {
        val store = SharedPreferencesAppVisibilityStore(context)
        store.setHidden("example.reinstalled", true)
        store.setHidden("example.other", true)

        val restoredStore = SharedPreferencesAppVisibilityStore(context)

        assertEquals(
            setOf("example.reinstalled", "example.other"),
            restoredStore.hiddenPackageNames,
        )
    }
}

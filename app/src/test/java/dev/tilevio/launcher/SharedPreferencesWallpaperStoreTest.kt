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
class SharedPreferencesWallpaperStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultsToCharcoal() {
        assertEquals(Wallpaper.Charcoal, SharedPreferencesWallpaperStore(context).wallpaper)
    }

    @Test
    fun freshStoreRestoresSelectedWallpaper() {
        SharedPreferencesWallpaperStore(context).wallpaper = Wallpaper.Slate

        val restoredStore = SharedPreferencesWallpaperStore(context)

        assertEquals(Wallpaper.Slate, restoredStore.wallpaper)
    }
}

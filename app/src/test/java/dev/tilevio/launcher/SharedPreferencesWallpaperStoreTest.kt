package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedPreferencesWallpaperStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "wallpaper-image").delete()
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

    @Test
    fun freshStoreRestoresImportedImageFromPrivateCopy() {
        Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            FileOutputStream(File(context.filesDir, "wallpaper-image")).use { output ->
                compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
            .edit()
            .putString("selected", "image")
            .commit()

        val restored = SharedPreferencesWallpaperStore(context).selection as WallpaperSelection.Image

        assertEquals(100, restored.bitmap.width)
        assertEquals(50, restored.bitmap.height)
        assertEquals(Color.RED, restored.bitmap.getPixel(50, 25))
    }
}

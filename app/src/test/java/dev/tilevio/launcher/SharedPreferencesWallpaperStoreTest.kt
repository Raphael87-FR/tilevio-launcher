package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
        assertEquals(WallpaperSelection.Solid(Wallpaper.Charcoal), SharedPreferencesWallpaperStore(context).selection)
    }

    @Test
    fun freshStoreRestoresSelectedWallpaper() {
        SharedPreferencesWallpaperStore(context).select(Wallpaper.Slate)

        val restoredStore = SharedPreferencesWallpaperStore(context)

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), restoredStore.selection)
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

    @Test
    fun importedImageIsAppliedAndRestoredByAFreshStore() {
        val uri = registerTestImage(context, "content://example/wallpaper", solidImage(4, 4, Color.RED)).uri

        val imported = SharedPreferencesWallpaperStore(context).importImage(uri, width = 1920, height = 1080)

        assertEquals(Color.RED, imported.bitmap.getPixel(0, 0))
        val restored = SharedPreferencesWallpaperStore(context).selection as WallpaperSelection.Image
        assertEquals(Color.RED, restored.bitmap.getPixel(0, 0))
    }

    @Test
    fun selectingASolidWallpaperAfterAnImageReplacesTheImage() {
        val store = SharedPreferencesWallpaperStore(context)
        store.importImage(
            registerTestImage(context, "content://example/wallpaper", solidImage(4, 4, Color.RED)).uri,
            width = 1920,
            height = 1080,
        )

        store.select(Wallpaper.Graphite)

        assertEquals(WallpaperSelection.Solid(Wallpaper.Graphite), store.selection)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Graphite), SharedPreferencesWallpaperStore(context).selection)
    }

    @Test
    fun storedImageIsDecodedOnlyOnce() {
        val store = SharedPreferencesWallpaperStore(context)
        store.importImage(
            registerTestImage(context, "content://example/wallpaper", solidImage(4, 4, Color.RED)).uri,
            width = 1920,
            height = 1080,
        )
        val restoredStore = SharedPreferencesWallpaperStore(context)

        val first = restoredStore.selection as WallpaperSelection.Image
        val second = restoredStore.selection as WallpaperSelection.Image

        assertSame(first.bitmap, second.bitmap)
    }

    @Test
    fun wideImageKeepsOnlyItsCenteredScreenPart() {
        val image = Bitmap.createBitmap(4000, 1000, Bitmap.Config.ARGB_8888)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                image.setPixel(x, y, if (x < 1000 || x >= 3000) Color.RED else Color.GREEN)
            }
        }
        val uri = registerTestImage(context, "content://example/wide", image).uri

        val imported = SharedPreferencesWallpaperStore(context).importImage(uri, width = 1920, height = 1080)

        assertEquals(1778, imported.bitmap.width)
        assertEquals(1000, imported.bitmap.height)
        assertEquals(Color.GREEN, imported.bitmap.getPixel(0, 500))
        assertEquals(Color.GREEN, imported.bitmap.getPixel(1777, 500))
    }

    @Test
    fun largeImageIsScaledDownToCoverTheScreenAndCentered() {
        val image = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                image.setPixel(x, y, if (y < 30 || y >= 270) Color.RED else Color.GREEN)
            }
        }
        val uri = registerTestImage(context, "content://example/large", image).uri

        val imported = SharedPreferencesWallpaperStore(context).importImage(uri, width = 192, height = 108)

        assertEquals(192, imported.bitmap.width)
        assertEquals(108, imported.bitmap.height)
        assertEquals(Color.GREEN, imported.bitmap.getPixel(96, 0))
        assertEquals(Color.GREEN, imported.bitmap.getPixel(96, 107))
    }

    @Test
    fun failedImportKeepsThePreviousWallpaper() {
        val store = SharedPreferencesWallpaperStore(context)
        store.select(Wallpaper.Slate)
        val uri = registerInvalidImage(context, "content://example/invalid").uri

        assertThrows(IOException::class.java) {
            store.importImage(uri, width = 1920, height = 1080)
        }

        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), store.selection)
        assertEquals(WallpaperSelection.Solid(Wallpaper.Slate), SharedPreferencesWallpaperStore(context).selection)
    }
}

package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.system.Os
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class Wallpaper(val id: String, val color: Color, val labelRes: Int) {
    Charcoal("charcoal", Color(0xFF171717), R.string.wallpaper_charcoal),
    Graphite("graphite", Color(0xFF292929), R.string.wallpaper_graphite),
    Slate("slate", Color(0xFF263238), R.string.wallpaper_slate),
    WarmGray("warm_gray", Color(0xFF2D2A26), R.string.wallpaper_warm_gray),
}

sealed interface WallpaperSelection {
    data class Solid(val wallpaper: Wallpaper) : WallpaperSelection
    data class Image(val bitmap: Bitmap) : WallpaperSelection
}

interface WallpaperStore {
    var wallpaper: Wallpaper
    val selection: WallpaperSelection

    fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image
}

class SharedPreferencesWallpaperStore(private val context: Context) : WallpaperStore {
    private val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val imageFile = File(context.filesDir, "wallpaper-image")

    override var wallpaper: Wallpaper
        get() {
            val id = preferences.getString("selected", null)
            return Wallpaper.entries.firstOrNull { wallpaper -> wallpaper.id == id } ?: Wallpaper.Charcoal
        }
        set(value) {
            preferences.edit().putString("selected", value.id).apply()
        }

    override val selection: WallpaperSelection
        get() {
            if (preferences.getString("selected", null) != IMAGE_ID) {
                return WallpaperSelection.Solid(wallpaper)
            }
            return BitmapFactory.decodeFile(imageFile.absolutePath)
                ?.let(WallpaperSelection::Image)
                ?: WallpaperSelection.Solid(Wallpaper.Charcoal)
        }

    override fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image {
        require(width > 0 && height > 0)
        val bitmap = decode(uri, width, height)
        val temporaryFile = File(context.filesDir, "wallpaper-image-${SystemClock.elapsedRealtimeNanos()}")
        try {
            FileOutputStream(temporaryFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not encode Wallpaper")
                }
                output.fd.sync()
            }
            Os.rename(temporaryFile.absolutePath, imageFile.absolutePath)
        } finally {
            temporaryFile.delete()
        }
        preferences.edit().putString("selected", IMAGE_ID).apply()
        return WallpaperSelection.Image(bitmap)
    }

    private fun decode(uri: Uri, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: throw IOException("Could not open image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Invalid image")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("Could not decode image")
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Int {
        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= width && sourceHeight / (sampleSize * 2) >= height) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val IMAGE_ID = "image"
    }
}

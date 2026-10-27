package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

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

/** Owns the Wallpaper selection. Reads and imports may decode an image: call them off the main thread. */
interface WallpaperStore {
    val selection: WallpaperSelection

    fun select(wallpaper: Wallpaper)

    /** Copies the image at [uri], bounded to a [width] x [height] screen, and selects it. */
    fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image
}

class SharedPreferencesWallpaperStore(private val context: Context) : WallpaperStore {
    private val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val imageFile = File(context.filesDir, "wallpaper-image")
    private val lock = Any()
    private var cachedSelection: WallpaperSelection? = null

    override val selection: WallpaperSelection
        get() {
            synchronized(lock) { cachedSelection }?.let { selection -> return selection }
            val selection = readSelection()
            return synchronized(lock) { cachedSelection ?: selection.also { cachedSelection = it } }
        }

    override fun select(wallpaper: Wallpaper) {
        update(WallpaperSelection.Solid(wallpaper))
    }

    private fun readSelection(): WallpaperSelection {
        val id = preferences.getString("selected", null)
        if (id != IMAGE_ID) {
            return WallpaperSelection.Solid(
                Wallpaper.entries.firstOrNull { wallpaper -> wallpaper.id == id } ?: Wallpaper.Charcoal,
            )
        }
        return BitmapFactory.decodeFile(imageFile.absolutePath)
            ?.let(WallpaperSelection::Image)
            ?: WallpaperSelection.Solid(Wallpaper.Charcoal)
    }

    override fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image {
        require(width > 0 && height > 0)
        val bitmap = cropToScreen(decode(uri, width, height), width, height)
        val temporaryFile = File(context.filesDir, "wallpaper-image-${SystemClock.elapsedRealtimeNanos()}")
        try {
            FileOutputStream(temporaryFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not encode Wallpaper")
                }
                output.fd.sync()
            }
            if (!temporaryFile.renameTo(imageFile)) {
                throw IOException("Could not store Wallpaper")
            }
        } finally {
            temporaryFile.delete()
        }
        return WallpaperSelection.Image(bitmap).also(::update)
    }

    private fun update(selection: WallpaperSelection) {
        val id = when (selection) {
            is WallpaperSelection.Solid -> selection.wallpaper.id
            is WallpaperSelection.Image -> IMAGE_ID
        }
        synchronized(lock) {
            preferences.edit().putString("selected", id).apply()
            cachedSelection = selection
        }
    }

    private fun decode(uri: Uri, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open image")
        input.use { BitmapFactory.decodeStream(input, null, bounds) }
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

    /** Keeps the centered part of [bitmap] that covers the screen, scaled down to the screen if larger. */
    private fun cropToScreen(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val coverScale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val cropWidth = minOf(bitmap.width, (width / coverScale).roundToInt())
        val cropHeight = minOf(bitmap.height, (height / coverScale).roundToInt())
        val scale = minOf(coverScale, 1f)
        val matrix = Matrix().apply { setScale(scale, scale) }
        return Bitmap.createBitmap(
            bitmap,
            (bitmap.width - cropWidth) / 2,
            (bitmap.height - cropHeight) / 2,
            cropWidth,
            cropHeight,
            matrix,
            true,
        ).also { cropped -> if (cropped !== bitmap) bitmap.recycle() }
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

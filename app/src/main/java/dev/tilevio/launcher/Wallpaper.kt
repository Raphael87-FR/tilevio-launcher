package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
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

    /** The selection when it is known without decoding an image, else null. Safe on the main thread. */
    val knownSelection: WallpaperSelection?

    fun select(wallpaper: Wallpaper)

    /**
     * Copies the image at [uri], bounded to a [width] x [height] screen, and selects it. Returns null,
     * keeping nothing, when a later choice was made before the import completed: the last choice wins.
     */
    fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image?
}

class SharedPreferencesWallpaperStore(private val context: Context) : WallpaperStore {
    private val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val imageFile = File(context.filesDir, "wallpaper-image")
    private val lock = Any()
    private var cachedSelection: WallpaperSelection? = null
    /** Incremented by every choice, so that an import can tell whether a later choice superseded it. */
    private var choiceCount = 0L

    override val selection: WallpaperSelection
        get() {
            synchronized(lock) { cachedSelection }?.let { selection -> return selection }
            val selection = readSelection()
            return synchronized(lock) { cachedSelection ?: selection.also { cachedSelection = it } }
        }

    override val knownSelection: WallpaperSelection?
        get() = synchronized(lock) { cachedSelection } ?: readSolidSelection()

    override fun select(wallpaper: Wallpaper) {
        synchronized(lock) {
            choiceCount++
            update(WallpaperSelection.Solid(wallpaper))
        }
    }

    private fun readSelection(): WallpaperSelection {
        return readSolidSelection()
            ?: BitmapFactory.decodeFile(imageFile.absolutePath)?.let(WallpaperSelection::Image)
            ?: WallpaperSelection.Solid(Wallpaper.Charcoal)
    }

    /** Null when an image is selected. */
    private fun readSolidSelection(): WallpaperSelection.Solid? {
        val id = preferences.getString("selected", null)
        if (id == IMAGE_ID) {
            return null
        }
        return WallpaperSelection.Solid(
            Wallpaper.entries.firstOrNull { wallpaper -> wallpaper.id == id } ?: Wallpaper.Charcoal,
        )
    }

    override fun importImage(uri: Uri, width: Int, height: Int): WallpaperSelection.Image? {
        require(width > 0 && height > 0)
        val importChoice = synchronized(lock) { ++choiceCount }
        val bitmap = decodeToScreen(uri, width, height)
        val temporaryFile = File(context.filesDir, "wallpaper-image-${SystemClock.elapsedRealtimeNanos()}")
        try {
            FileOutputStream(temporaryFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not encode Wallpaper")
                }
                output.fd.sync()
            }
            synchronized(lock) {
                if (importChoice != choiceCount) {
                    return null
                }
                if (!temporaryFile.renameTo(imageFile)) {
                    throw IOException("Could not store Wallpaper")
                }
                return WallpaperSelection.Image(bitmap).also(::update)
            }
        } finally {
            temporaryFile.delete()
        }
    }

    /** Call with [lock] held. */
    private fun update(selection: WallpaperSelection) {
        val id = when (selection) {
            is WallpaperSelection.Solid -> selection.wallpaper.id
            is WallpaperSelection.Image -> IMAGE_ID
        }
        preferences.edit().putString("selected", id).apply()
        cachedSelection = selection
    }

    /**
     * Decodes the centered part of the image at [uri] that covers the screen, scaled down to the
     * screen if larger. The decoder scales while decoding, so the full-size image is never in memory.
     */
    private fun decodeToScreen(uri: Uri, width: Int, height: Int): Bitmap {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val coverScale = maxOf(width.toFloat() / info.size.width, height.toFloat() / info.size.height)
            val scale = minOf(coverScale, 1f)
            val targetWidth = (info.size.width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (info.size.height * scale).roundToInt().coerceAtLeast(1)
            val cropWidth = minOf(targetWidth, (width * scale / coverScale).roundToInt())
            val cropHeight = minOf(targetHeight, (height * scale / coverScale).roundToInt())
            val left = (targetWidth - cropWidth) / 2
            val top = (targetHeight - cropHeight) / 2
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(targetWidth, targetHeight)
            decoder.crop = Rect(left, top, left + cropWidth, top + cropHeight)
        }
    }

    private companion object {
        const val IMAGE_ID = "image"
    }
}

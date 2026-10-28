package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.withClip
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class Wallpaper(val id: String, val color: Color, val labelRes: Int) {
    Charcoal("charcoal", Color(0xFF171717), R.string.wallpaper_charcoal),
    Graphite("graphite", Color(0xFF292929), R.string.wallpaper_graphite),
    Slate("slate", Color(0xFF263238), R.string.wallpaper_slate),
    WarmGray("warm_gray", Color(0xFF2D2A26), R.string.wallpaper_warm_gray),
}

/** The image types an import can decode: those [BitmapRegionDecoder] reads. */
val wallpaperImageTypes = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

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
                // The result is opaque. As JPEG, a photo takes a fraction of its PNG size and decodes faster.
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
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
     * screen if larger, upright and over the window background. Only that part is decoded, a few
     * rows at a time straight into the result, so the import holds little more than one screen.
     */
    private fun decodeToScreen(uri: Uri, width: Int, height: Int): Bitmap {
        val decoder = openImage(uri).use { input ->
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(input, false)
        } ?: throw IOException("Could not decode image")
        try {
            val exifOrientation = openImage(uri).use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            val toResult = uprightMatrix(exifOrientation)
            val upright = RectF(0f, 0f, decoder.width.toFloat(), decoder.height.toFloat()).also(toResult::mapRect)
            val coverScale = maxOf(width / upright.width(), height / upright.height())
            val scale = minOf(coverScale, 1f)
            val scaledWidth = upright.width() * scale
            val scaledHeight = upright.height() * scale
            val resultWidth = minOf(scaledWidth.roundToInt(), (width * scale / coverScale).roundToInt()).coerceAtLeast(1)
            val resultHeight = minOf(scaledHeight.roundToInt(), (height * scale / coverScale).roundToInt()).coerceAtLeast(1)
            toResult.postTranslate(-upright.left, -upright.top)
            toResult.postScale(scale, scale)
            toResult.postTranslate((resultWidth - scaledWidth) / 2, (resultHeight - scaledHeight) / 2)

            // The part of the image that reaches the result, aligned on the sample grid so that strips meet exactly.
            val sampleSize = (1 / scale).toInt().coerceAtLeast(1)
            val visible = RectF(0f, 0f, resultWidth.toFloat(), resultHeight.toFloat())
                .also(Matrix().also(toResult::invert)::mapRect)
            fun alignDown(value: Float) = floor(value / sampleSize).toInt() * sampleSize
            fun alignUp(value: Float) = ceil(value / sampleSize).toInt() * sampleSize
            val region = Rect(
                alignDown(visible.left).coerceAtLeast(0),
                alignDown(visible.top).coerceAtLeast(0),
                alignUp(visible.right).coerceAtMost(decoder.width),
                alignUp(visible.bottom).coerceAtMost(decoder.height),
            )
            val stripPixels = resultWidth * resultHeight / STRIPS_PER_RESULT
            val stripHeight = (stripPixels / (region.width() / sampleSize).coerceAtLeast(1)).coerceAtLeast(1) * sampleSize

            val result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            // A transparent image shows the window background, as it did when displayed.
            canvas.drawColor(context.getColor(R.color.wallpaper))
            canvas.concat(toResult)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for (top in region.top until region.bottom step stripHeight) {
                val strip = Rect(region.left, top, region.right, minOf(top + stripHeight, region.bottom))
                // One more sample around the strip, so that filtering at its edges blends real neighbours.
                val decoded = Rect(
                    maxOf(strip.left - sampleSize, 0),
                    maxOf(strip.top - sampleSize, 0),
                    minOf(strip.right + sampleSize, decoder.width),
                    minOf(strip.bottom + sampleSize, decoder.height),
                )
                val pixels = decoder.decodeRegion(decoded, options) ?: throw IOException("Could not decode image")
                canvas.withClip(strip) { drawBitmap(pixels, null, decoded, paint) }
                pixels.recycle()
            }
            return result
        } finally {
            decoder.recycle()
        }
    }

    private fun openImage(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open image")

    /** Maps stored pixels to how the image is meant to be seen, up to a translation. */
    private fun uprightMatrix(exifOrientation: Int): Matrix = Matrix().apply {
        when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(270f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }

    private companion object {
        const val IMAGE_ID = "image"
        const val JPEG_QUALITY = 90
        /** A strip decodes about the result's pixels divided by this. */
        const val STRIPS_PER_RESULT = 4
    }
}

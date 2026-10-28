package dev.tilevio.launcher

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.robolectric.Shadows.shadowOf

/** A document served by the Robolectric content resolver, as an image picker would return it. */
class TestDocument(val uri: Uri, private val bytes: ByteArray) {
    var openCount = 0
        private set
    var isAvailable = true
    /** Runs while the document is being opened, as a slow read would let a concurrent action do. */
    var onOpen: () -> Unit = {}

    internal fun open(): ByteArrayInputStream {
        openCount++
        onOpen()
        return ByteArrayInputStream(if (isAvailable) bytes else ByteArray(0))
    }
}

fun registerTestImage(context: Context, uri: String, image: Bitmap): TestDocument {
    val bytes = ByteArrayOutputStream().use { output ->
        image.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
    return registerTestDocument(context, uri, bytes)
}

fun registerTestDocument(context: Context, uri: String, bytes: ByteArray): TestDocument {
    val document = TestDocument(Uri.parse(uri), bytes)
    shadowOf(context.contentResolver).registerInputStreamSupplier(document.uri) { document.open() }
    return document
}

fun registerInvalidImage(context: Context, uri: String): TestDocument =
    registerTestDocument(context, uri, "not an image".toByteArray())

fun solidImage(width: Int, height: Int, color: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

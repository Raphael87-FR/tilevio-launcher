package dev.tilevio.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

/** Loads banners bounded to a Tile [tileWidthPx] wide, so they are not upscaled on screen. */
class AndroidLaunchableApps(
    private val packageManager: PackageManager,
    private val tileWidthPx: Int,
) : LaunchableApps {
    /** One App per package, preferring its TV entry; bitmaps are loaded only for the kept entry. */
    override fun load(): List<App> =
        (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { resolveInfo -> resolveInfo.activityInfo.packageName }
            .map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo
                App(
                    componentName = ComponentName(activityInfo.packageName, activityInfo.name),
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = resolveInfo.loadIcon(packageManager).toBoundedBitmap(maxWidth = MAX_ICON_SIZE_PX, maxHeight = MAX_ICON_SIZE_PX),
                    banner = activityInfo.loadBanner(packageManager)
                        ?.toBoundedBitmap(maxWidth = tileWidthPx, maxHeight = tileWidthPx * 9 / 16),
                )
            }

    private fun query(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return packageManager.queryIntentActivities(intent, 0).filter { resolveInfo -> resolveInfo.activityInfo != null }
    }
}

private const val MAX_ICON_SIZE_PX = 192

private fun Drawable.toBoundedBitmap(maxWidth: Int, maxHeight: Int): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { width -> width > 0 } ?: 1
    val sourceHeight = intrinsicHeight.takeIf { height -> height > 0 } ?: 1
    val scale = minOf(
        maxWidth.toFloat() / sourceWidth,
        maxHeight.toFloat() / sourceHeight,
        1f,
    )
    return toBitmap(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1),
    )
}

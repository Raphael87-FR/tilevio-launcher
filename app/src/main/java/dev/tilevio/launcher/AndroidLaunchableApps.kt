package dev.tilevio.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

class AndroidLaunchableApps(
    private val packageManager: PackageManager,
) : LaunchableApps {
    override fun load(): List<App> = buildList {
        addAll(query(Intent.CATEGORY_LEANBACK_LAUNCHER))
        addAll(query(Intent.CATEGORY_LAUNCHER))
    }

    private fun query(category: String): List<App> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val activities = packageManager.queryIntentActivities(intent, 0)
        return activities.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            App(
                componentName = ComponentName(packageName, activityInfo.name),
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager).toBoundedBitmap(),
                isTelevisionEntry = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
                banner = activityInfo.loadBanner(packageManager)?.toBoundedBitmap(),
            )
        }
    }
}

private const val MAX_ICON_SIZE_PX = 192

private fun Drawable.toBoundedBitmap(): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { width -> width > 0 } ?: 1
    val sourceHeight = intrinsicHeight.takeIf { height -> height > 0 } ?: 1
    val scale = minOf(
        MAX_ICON_SIZE_PX.toFloat() / sourceWidth,
        MAX_ICON_SIZE_PX.toFloat() / sourceHeight,
        1f,
    )
    return toBitmap(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1),
    )
}

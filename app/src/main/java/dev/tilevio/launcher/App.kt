package dev.tilevio.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import java.text.Collator

data class App(
    val componentName: ComponentName,
    val label: String,
    val icon: Bitmap,
    val isTelevisionEntry: Boolean,
) {
    val key: String
        get() = componentName.flattenToString()
}

fun interface LaunchableApps {
    fun load(): List<App>
}

fun interface AppCatalog {
    fun loadApps(): List<App>
}

class InstalledAppCatalog(
    private val launchableApps: LaunchableApps,
    private val ownPackageName: String,
) : AppCatalog {
    override fun loadApps(): List<App> {
        val collator = Collator.getInstance()
        return launchableApps.load()
            .filterNot { app -> app.componentName.packageName == ownPackageName }
            .groupBy { app -> app.componentName.packageName }
            .map { (_, entries) ->
                entries.firstOrNull { app -> app.isTelevisionEntry } ?: entries.first()
            }
            .sortedWith { left, right ->
                collator.compare(left.label, right.label)
                    .takeUnless { result -> result == 0 }
                    ?: left.componentName.packageName.compareTo(right.componentName.packageName)
            }
    }
}

fun interface AppLauncher {
    fun launch(app: App): Boolean
}

data class LauncherDependencies(
    val catalog: AppCatalog,
    val appLauncher: AppLauncher,
    val appVisibilityStore: AppVisibilityStore,
    val packageChangeEvents: PackageChangeEvents,
    val homeRole: HomeRoleAdapter,
    val homeRolePromptStore: HomeRolePromptStore,
    val wallpaperStore: WallpaperStore,
)

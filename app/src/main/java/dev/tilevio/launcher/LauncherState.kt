package dev.tilevio.launcher

import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Collator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherState(
    private val dependencies: LauncherDependencies,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    var apps: List<App> by mutableStateOf(emptyList())
        private set
    private var hiddenPackageNames by mutableStateOf(dependencies.appVisibilityStore.hiddenPackageNames)
    val visibleApps: List<App> by derivedStateOf {
        apps.filterNot { app -> app.componentName.packageName in hiddenPackageNames }
    }
    val hiddenApps: List<App> by derivedStateOf {
        apps.filter { app -> app.componentName.packageName in hiddenPackageNames }
    }
    /** Null until the Wallpaper store has been read. */
    var wallpaper: WallpaperSelection? by mutableStateOf(null)
        private set
    var message: LauncherMessage? by mutableStateOf(null)
        private set
    private var loadJob: Job? = null
    private val wallpaperLoadJob = scope.launch {
        wallpaper = withContext(ioDispatcher) { dependencies.wallpaperStore.selection }
    }
    private var importJob: Job? = null
    private var messageJob: Job? = null

    /** Loads the App catalog and reloads it on package changes until the returned function is called. */
    fun watchCatalog(): () -> Unit {
        val unregister = dependencies.packageChangeEvents.register(::refresh)
        refresh()
        return unregister
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            apps = withContext(ioDispatcher) {
                dependencies.launchableApps.load().toCatalog(dependencies.ownPackageName)
            }
        }
    }

    fun setHidden(app: App, hidden: Boolean) {
        dependencies.appVisibilityStore.setHidden(app.componentName.packageName, hidden)
        hiddenPackageNames = dependencies.appVisibilityStore.hiddenPackageNames
    }

    fun launch(app: App) {
        if (!dependencies.appLauncher.launch(app)) {
            show(LauncherMessage.LaunchFailure(app.label))
            refresh()
        }
    }

    fun selectWallpaper(wallpaper: Wallpaper) {
        wallpaperLoadJob.cancel()
        dependencies.wallpaperStore.select(wallpaper)
        this.wallpaper = WallpaperSelection.Solid(wallpaper)
    }

    fun chooseWallpaperImage(openPicker: () -> Unit) {
        try {
            openPicker()
        } catch (exception: Exception) {
            show(LauncherMessage.WallpaperImportFailure)
        }
    }

    fun importWallpaperImage(uri: Uri, width: Int, height: Int) {
        importJob?.cancel()
        importJob = scope.launch {
            try {
                val image = withContext(ioDispatcher) {
                    dependencies.wallpaperStore.importImage(uri = uri, width = width, height = height)
                }
                wallpaperLoadJob.cancel()
                wallpaper = image
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                show(LauncherMessage.WallpaperImportFailure)
            }
        }
    }

    private fun show(message: LauncherMessage) {
        if (message == this.message) {
            return
        }
        this.message = message
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(4_000)
            this@LauncherState.message = null
        }
    }
}

/** One App per package, preferring its TV entry, without Tilevio itself, sorted by label. */
private fun List<App>.toCatalog(ownPackageName: String): List<App> {
    val collator = Collator.getInstance()
    return filterNot { app -> app.componentName.packageName == ownPackageName }
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

sealed interface LauncherMessage {
    data class LaunchFailure(val appLabel: String) : LauncherMessage
    data object WallpaperImportFailure : LauncherMessage
}

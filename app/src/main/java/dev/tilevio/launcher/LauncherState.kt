package dev.tilevio.launcher

import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var wallpaper: WallpaperSelection by mutableStateOf(dependencies.wallpaperStore.selection)
        private set
    var isDefaultHome by mutableStateOf(dependencies.homeRole.isHeld)
        private set
    var message: LauncherMessage? by mutableStateOf(null)
        private set
    private var loadJob: Job? = null
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
            apps = withContext(ioDispatcher) { dependencies.catalog.loadApps() }
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
        this.wallpaper = WallpaperSelection.Solid(wallpaper)
        dependencies.wallpaperStore.wallpaper = wallpaper
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
                wallpaper = withContext(ioDispatcher) {
                    dependencies.wallpaperStore.importImage(uri = uri, width = width, height = height)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                show(LauncherMessage.WallpaperImportFailure)
            }
        }
    }

    /** Returns true once, on the first launch where Tilevio is not yet the Default Home. */
    fun takeInitialDefaultHomeRequest(): Boolean {
        val promptStore = dependencies.homeRolePromptStore
        if (dependencies.homeRole.isHeld || promptStore.hasHandledInitialRequest) {
            return false
        }
        promptStore.hasHandledInitialRequest = true
        return true
    }

    fun refreshDefaultHome() {
        isDefaultHome = dependencies.homeRole.isHeld
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

sealed interface LauncherMessage {
    data class LaunchFailure(val appLabel: String) : LauncherMessage
    data object WallpaperImportFailure : LauncherMessage
}

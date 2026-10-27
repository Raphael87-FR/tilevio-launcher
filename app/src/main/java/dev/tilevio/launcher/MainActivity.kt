package dev.tilevio.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var dependencies: LauncherDependencies
    private var isSettingsVisible by mutableStateOf(false)
    private val defaultHomeRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        dependencies.defaultHome.refresh()
    }
    private var wallpaperImageResult by mutableStateOf<WallpaperImageResult?>(null)
    private val wallpaperImageRequest = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        wallpaperImageResult = WallpaperImageResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dependencies = (application as TilevioApplication).launcherDependencies
        dependencies.defaultHome.refresh()
        onBackPressedDispatcher.addCallback(this) {
            if (isSettingsVisible) {
                isSettingsVisible = false
            }
        }
        setContent {
            LauncherScreen(
                dependencies = dependencies,
                isSettingsVisible = isSettingsVisible,
                onSettingsVisibilityChange = { isSettingsVisible = it },
                onActivateDefaultHome = ::activateDefaultHome,
                onPickWallpaperImage = { wallpaperImageRequest.launch(arrayOf("image/*")) },
                wallpaperImageResult = wallpaperImageResult,
                onWallpaperImageResultHandled = { wallpaperImageResult = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        dependencies.defaultHome.refresh()
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        isSettingsVisible = false
    }

    private fun activateDefaultHome(activation: DefaultHomeActivation) {
        when (activation) {
            is DefaultHomeActivation.Request -> defaultHomeRequest.launch(activation.intent)
            is DefaultHomeActivation.HomeSettings -> startActivity(activation.intent)
        }
    }
}

class WallpaperImageResult(val uri: Uri?)

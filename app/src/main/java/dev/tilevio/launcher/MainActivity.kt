package dev.tilevio.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var dependencies: LauncherDependencies
    private var homeRoleVersion by mutableIntStateOf(0)
    private var isSettingsVisible by mutableStateOf(false)
    private val homeRoleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        homeRoleVersion++
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
        onBackPressedDispatcher.addCallback(this) {
            if (isSettingsVisible) {
                isSettingsVisible = false
            }
        }
        setContent {
            LauncherScreen(
                dependencies = dependencies,
                homeRoleVersion = homeRoleVersion,
                isSettingsVisible = isSettingsVisible,
                onSettingsVisibilityChange = { isSettingsVisible = it },
                onActivateHomeRole = ::activateHomeRole,
                onPickWallpaperImage = { wallpaperImageRequest.launch(arrayOf("image/*")) },
                wallpaperImageResult = wallpaperImageResult,
                onWallpaperImageResultHandled = { wallpaperImageResult = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        homeRoleVersion++
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        isSettingsVisible = false
    }

    private fun activateHomeRole() {
        if (dependencies.homeRole.isAvailable) {
            homeRoleRequest.launch(dependencies.homeRole.createRequestIntent())
        } else {
            startActivity(dependencies.homeRole.createHomeSettingsIntent())
        }
    }
}

class WallpaperImageResult(val uri: Uri?)

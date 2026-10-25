package dev.tilevio.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as TilevioApplication).launcherDependencies
        setContent {
            LauncherScreen(dependencies)
        }
    }
}

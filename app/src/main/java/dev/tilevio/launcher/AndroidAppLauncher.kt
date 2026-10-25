package dev.tilevio.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

class AndroidAppLauncher(
    private val context: Context,
) : AppLauncher {
    override fun launch(app: App): Boolean = try {
        val intent = Intent.makeMainActivity(app.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

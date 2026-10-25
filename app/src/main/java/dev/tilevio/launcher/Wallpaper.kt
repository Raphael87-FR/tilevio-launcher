package dev.tilevio.launcher

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class Wallpaper(val id: String, val color: Color, val labelRes: Int) {
    Charcoal("charcoal", Color(0xFF171717), R.string.wallpaper_charcoal),
    Graphite("graphite", Color(0xFF292929), R.string.wallpaper_graphite),
    Slate("slate", Color(0xFF263238), R.string.wallpaper_slate),
    WarmGray("warm_gray", Color(0xFF2D2A26), R.string.wallpaper_warm_gray),
}

interface WallpaperStore {
    var wallpaper: Wallpaper
}

class SharedPreferencesWallpaperStore(context: Context) : WallpaperStore {
    private val preferences = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)

    override var wallpaper: Wallpaper
        get() {
            val id = preferences.getString("selected", null)
            return Wallpaper.entries.firstOrNull { wallpaper -> wallpaper.id == id } ?: Wallpaper.Charcoal
        }
        set(value) {
            preferences.edit().putString("selected", value.id).apply()
        }
}

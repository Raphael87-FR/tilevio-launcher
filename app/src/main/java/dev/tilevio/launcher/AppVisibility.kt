package dev.tilevio.launcher

import android.content.Context

interface AppVisibilityStore {
    val hiddenPackageNames: Set<String>

    fun setHidden(packageName: String, hidden: Boolean)
}

class SharedPreferencesAppVisibilityStore(context: Context) : AppVisibilityStore {
    private val preferences = context.getSharedPreferences("app-visibility", Context.MODE_PRIVATE)

    override val hiddenPackageNames: Set<String>
        get() = preferences.getStringSet(HIDDEN_PACKAGES, emptySet())?.toSet().orEmpty()

    override fun setHidden(packageName: String, hidden: Boolean) {
        val updated = hiddenPackageNames.toMutableSet().apply {
            if (hidden) add(packageName) else remove(packageName)
        }
        preferences.edit().putStringSet(HIDDEN_PACKAGES, updated).apply()
    }

    private companion object {
        const val HIDDEN_PACKAGES = "hidden-packages"
    }
}

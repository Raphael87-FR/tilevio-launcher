package dev.tilevio.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class AndroidHomeRole(context: Context) : HomeRoleAdapter {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    override val isAvailable: Boolean
        get() = roleManager.isRoleAvailable(RoleManager.ROLE_HOME)

    override val isHeld: Boolean
        get() = roleManager.isRoleHeld(RoleManager.ROLE_HOME)

    override fun createRequestIntent(): Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)

    override fun createHomeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}

class SharedPreferencesHomeRolePromptStore(context: Context) : HomeRolePromptStore {
    private val preferences = context.getSharedPreferences("home_role", Context.MODE_PRIVATE)

    override var hasHandledInitialRequest: Boolean
        get() = preferences.getBoolean("initial_request_handled", false)
        set(value) = preferences.edit().putBoolean("initial_request_handled", value).apply()
}
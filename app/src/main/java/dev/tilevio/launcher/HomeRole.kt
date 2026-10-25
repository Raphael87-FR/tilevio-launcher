package dev.tilevio.launcher

import android.content.Intent

interface HomeRoleAdapter {
    val isAvailable: Boolean
    val isHeld: Boolean

    fun createRequestIntent(): Intent

    fun createHomeSettingsIntent(): Intent
}

interface HomeRolePromptStore {
    var hasHandledInitialRequest: Boolean
}

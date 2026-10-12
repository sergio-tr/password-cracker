package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.assessment.port.LabModePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [LabModePreferences.isLabModeEnabled]. Default **false**.
 * Emits [enabled] so navigation and CTAs update at runtime without process restart.
 */
class SharedPreferencesLabModePreferences(
    context: Context,
) : LabModePreferences {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun isLabModeEnabled(): Boolean = _enabled.value

    override fun setLabModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        private const val PREFS = "lab_mode_preferences"
        private const val KEY_ENABLED = "lab_mode_enabled"
    }
}

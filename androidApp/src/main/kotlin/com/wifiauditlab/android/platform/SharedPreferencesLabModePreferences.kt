package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.assessment.port.LabModePreferences

/**
 * Persists [LabModePreferences.isLabModeEnabled]. Default **false**.
 * Enabling lab mode does not authorize AP/LAB validation by itself.
 */
class SharedPreferencesLabModePreferences(
    context: Context,
) : LabModePreferences {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isLabModeEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setLabModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS = "lab_mode_preferences"
        private const val KEY_ENABLED = "lab_mode_enabled"
    }
}

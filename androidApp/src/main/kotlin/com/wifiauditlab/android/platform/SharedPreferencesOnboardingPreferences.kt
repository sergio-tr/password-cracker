package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.assessment.port.OnboardingPreferences

class SharedPreferencesOnboardingPreferences(
    context: Context,
) : OnboardingPreferences {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    override fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    companion object {
        private const val PREFS = "onboarding"
        private const val KEY_COMPLETED = "completed"
    }
}

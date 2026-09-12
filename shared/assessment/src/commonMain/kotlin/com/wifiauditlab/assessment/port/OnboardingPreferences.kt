package com.wifiauditlab.assessment.port

/**
 * Remembers whether the first-run onboarding was finished or skipped.
 * Platform adapters persist this flag; domain does not care how.
 */
interface OnboardingPreferences {
    fun isCompleted(): Boolean

    fun markCompleted()
}

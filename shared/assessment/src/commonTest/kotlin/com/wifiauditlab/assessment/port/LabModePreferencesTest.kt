package com.wifiauditlab.assessment.port

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLabModePreferences : LabModePreferences {
    private var enabled: Boolean = false

    override fun isLabModeEnabled(): Boolean = enabled

    override fun setLabModeEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

class LabModePreferencesTest {
    @Test
    fun defaultsToDisabled_andPersistsToggle() {
        val prefs = InMemoryLabModePreferences()
        assertFalse(prefs.isLabModeEnabled())
        prefs.setLabModeEnabled(true)
        assertTrue(prefs.isLabModeEnabled())
        prefs.setLabModeEnabled(false)
        assertFalse(prefs.isLabModeEnabled())
    }
}

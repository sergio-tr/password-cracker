package com.wifiauditlab.assessment.port

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryLabModePreferences : LabModePreferences {
    private val _enabled = MutableStateFlow(false)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun isLabModeEnabled(): Boolean = _enabled.value

    override fun setLabModeEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }
}

class LabModePreferencesTest {
    @Test
    fun defaultsToDisabled_andPersistsToggle() {
        val prefs = InMemoryLabModePreferences()
        assertFalse(prefs.isLabModeEnabled())
        assertFalse(prefs.enabled.value)
        prefs.setLabModeEnabled(true)
        assertTrue(prefs.isLabModeEnabled())
        assertTrue(prefs.enabled.value)
        prefs.setLabModeEnabled(false)
        assertFalse(prefs.isLabModeEnabled())
        assertFalse(prefs.enabled.value)
    }
}

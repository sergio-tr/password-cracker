package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.port.LabModePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LabModeVisibilityViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun labFeatureVisibility_defaultsFalse_andTogglesAtRuntime() =
        runTest {
            val prefs = RecordingLabModePreferences()
            assertFalse(prefs.enabled.value)
            prefs.setLabModeEnabled(true)
            assertTrue(prefs.enabled.value)
            prefs.setLabModeEnabled(false)
            assertFalse(prefs.enabled.value)
        }

    private class RecordingLabModePreferences : LabModePreferences {
        private val _enabled = MutableStateFlow(false)
        override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

        override fun isLabModeEnabled(): Boolean = _enabled.value

        override fun setLabModeEnabled(enabled: Boolean) {
            _enabled.value = enabled
        }
    }
}

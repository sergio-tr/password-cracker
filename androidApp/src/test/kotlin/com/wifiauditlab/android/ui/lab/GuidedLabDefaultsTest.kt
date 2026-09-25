package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedLabDefaultsTest {
    @Test
    fun recommendedConfig_usesLengthStrategyAndBoundedWorkers() {
        val config =
            GuidedLabDefaults.recommendedConfig(
                availableProcessors = 8,
                calibratedAttemptsPerSecond = 80_000.0,
            )
        assertEquals(StrategyChoice.LENGTH, config.strategy)
        assertEquals(AlphabetChoice.DIGITS, config.alphabet)
        assertEquals(4, config.secretLength)
        assertTrue(config.workers in 1..4)
        assertEquals(2_000_000L, config.maxAttempts)
        assertEquals(45L, config.maxDurationSeconds)
    }

    @Test
    fun lowThroughput_prefersSingleWorker() {
        val config =
            GuidedLabDefaults.recommendedConfig(
                availableProcessors = 8,
                calibratedAttemptsPerSecond = 100.0,
            )
        assertEquals(1, config.workers)
    }

    @Test
    fun sharedPasswordDemo_onlyForPersonalFamilies() {
        assertTrue(SecurityFamily.WPA2_PERSONAL.supportsSharedPasswordDemo())
        assertTrue(SecurityFamily.WPA3_PERSONAL.supportsSharedPasswordDemo())
        assertTrue(SecurityFamily.WPA2_WPA3_PERSONAL.supportsSharedPasswordDemo())
        assertTrue(SecurityFamily.WEP.supportsSharedPasswordDemo())
        assertFalse(SecurityFamily.OPEN.supportsSharedPasswordDemo())
        assertFalse(SecurityFamily.OWE.supportsSharedPasswordDemo())
        assertFalse(SecurityFamily.WPA2_ENTERPRISE.supportsSharedPasswordDemo())
        assertFalse(SecurityFamily.DPP.supportsSharedPasswordDemo())
        assertEquals(R.string.lab_guided_open_owe, SecurityFamily.OPEN.guidedLabExplanationRes())
    }
}

package com.wifiauditlab.android.ui.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class LabConfigSearchSpaceTest {
    @Test
    fun resolvedLengthPolicy_singleSize_whenRangeUnset() {
        val policy = LabConfig(secretLength = 6).resolvedLengthPolicy()
        assertEquals(6, policy.minLength)
        assertEquals(6, policy.maxLength)
    }

    @Test
    fun resolvedLengthPolicy_onlyMax_usesProtocolMin() {
        val policy =
            LabConfig(lengthMax = 12).resolvedLengthPolicy(protocolMin = 8, softMax = 16)
        assertEquals(8, policy.minLength)
        assertEquals(12, policy.maxLength)
    }

    @Test
    fun resolvedLengthPolicy_onlyMin_usesSoftMax() {
        val policy =
            LabConfig(lengthMin = 10).resolvedLengthPolicy(protocolMin = 8, softMax = 16)
        assertEquals(10, policy.minLength)
        assertEquals(16, policy.maxLength)
    }

    @Test
    fun resolvedLengthPolicy_bothBounds() {
        val policy =
            LabConfig(lengthMin = 8, lengthMax = 14).resolvedLengthPolicy(protocolMin = 8, softMax = 16)
        assertEquals(8, policy.minLength)
        assertEquals(14, policy.maxLength)
    }

    @Test
    fun resolvedLengthPolicy_clampsToSoftMax() {
        val policy =
            LabConfig(lengthMin = 8, lengthMax = 40).resolvedLengthPolicy(protocolMin = 8, softMax = 16)
        assertEquals(8, policy.minLength)
        assertEquals(16, policy.maxLength)
    }
}

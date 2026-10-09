package com.wifiauditlab.assessment.domain.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalOnlyFailureReasonMapperTest {
    @Test
    fun mapsAuthenticationWithoutCallingItWrongPassword() {
        assertEquals(
            NetworkValidationResult.AuthenticationRejected,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.AUTHENTICATION),
        )
        assertTrue(
            !LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.AUTHENTICATION)
                .category
                .contains("WRONG", ignoreCase = true),
        )
    }

    @Test
    fun mapsKnownPlatformReasons() {
        assertEquals(
            NetworkValidationResult.AssociationFailed,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.ASSOCIATION),
        )
        assertEquals(
            NetworkValidationResult.IpProvisioningFailed,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.IP_PROVISIONING),
        )
        assertEquals(
            NetworkValidationResult.NetworkNotFound,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.NOT_FOUND),
        )
        assertEquals(
            NetworkValidationResult.NoResponse,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.NO_RESPONSE),
        )
        assertEquals(
            NetworkValidationResult.UserRejected,
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.USER_REJECT),
        )
        assertIs<NetworkValidationResult.Inconclusive>(
            LocalOnlyFailureReasonMapper.map(LocalOnlyFailureReasonMapper.UNKNOWN),
        )
    }

    @Test
    fun validationCredential_redactsAndClears() {
        val secret = "one-shot-secret"
        val credential = ValidationCredential.fromPlaintext(secret)
        assertTrue(!credential.toString().contains(secret))
        val used = credential.use { it }
        assertEquals(secret, used)
        assertTrue(!credential.isPresent())
        assertEquals(null, credential.use { it })
    }

    @Test
    fun supportsLabNetworkValidation_families() {
        assertTrue(com.wifiauditlab.assessment.domain.wifi.SecurityFamily.WPA2_PERSONAL.supportsLabNetworkValidation())
        assertTrue(com.wifiauditlab.assessment.domain.wifi.SecurityFamily.WPA3_PERSONAL.supportsLabNetworkValidation())
        assertTrue(
            !com.wifiauditlab.assessment.domain.wifi.SecurityFamily.WEP.supportsLabNetworkValidation(),
        )
        assertTrue(
            !com.wifiauditlab.assessment.domain.wifi.SecurityFamily.OPEN.supportsLabNetworkValidation(),
        )
    }
}

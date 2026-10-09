package com.wifiauditlab.assessment.domain.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidNetworkValidationUnavailableReasonMappingTest {
    @Test
    fun mapsPermissionAndFamilyToDistinctApAuthReasons() {
        assertEquals(
            ApAuthUnavailableReason.PermissionMissing,
            AndroidNetworkValidationUnavailableReason.MissingNearbyOrLocationPermission.toApAuthReason(),
        )
        assertEquals(
            ApAuthUnavailableReason.PermissionMissing,
            AndroidNetworkValidationUnavailableReason.MissingWifiStatePermission.toApAuthReason(),
        )
        assertEquals(
            ApAuthUnavailableReason.UnsupportedSecurityFamily,
            AndroidNetworkValidationUnavailableReason.UnsupportedSecurityFamily.toApAuthReason(),
        )
        assertEquals(
            ApAuthUnavailableReason.RequiresApi34,
            AndroidNetworkValidationUnavailableReason.RequiresApi34.toApAuthReason(),
        )
        assertEquals(
            ApAuthUnavailableReason.RequiresStaConcurrency,
            AndroidNetworkValidationUnavailableReason.RequiresStaConcurrencyForLocalOnly.toApAuthReason(),
        )
    }
}

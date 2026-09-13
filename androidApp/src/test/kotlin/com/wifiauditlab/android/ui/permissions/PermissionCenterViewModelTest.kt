package com.wifiauditlab.android.ui.permissions

import com.wifiauditlab.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCenterViewModelTest {
    private fun inventory(
        wifiStatus: PermissionStatus,
        locationStatus: PermissionStatus = PermissionStatus.Enabled,
        permanentlyDenied: Set<String> = emptySet(),
    ): PermissionInventory =
        object : PermissionInventory {
            override fun refresh(): List<PermissionItem> {
                val wifi =
                    when {
                        wifiStatus == PermissionStatus.Granted ->
                            PermissionItem(
                                id = "wifi_discovery",
                                nameRes = R.string.permissions_wifi_discovery,
                                kind = PermissionKind.RequiredPermission,
                                status = PermissionStatus.Granted,
                                rationaleRes = R.string.permissions_wifi_rationale_tiramisu,
                                action = PermissionAction.None,
                            )
                        "NEARBY_WIFI_DEVICES" in permanentlyDenied ||
                            "ACCESS_FINE_LOCATION" in permanentlyDenied ->
                            PermissionItem(
                                id = "wifi_discovery",
                                nameRes = R.string.permissions_wifi_discovery,
                                kind = PermissionKind.RequiredPermission,
                                status = PermissionStatus.PermanentlyDenied,
                                rationaleRes = R.string.permissions_wifi_rationale_tiramisu,
                                action = PermissionAction.OpenAppSettings,
                            )
                        else ->
                            PermissionItem(
                                id = "wifi_discovery",
                                nameRes = R.string.permissions_wifi_discovery,
                                kind = PermissionKind.RequiredPermission,
                                status = wifiStatus,
                                rationaleRes = R.string.permissions_wifi_rationale_tiramisu,
                                action =
                                    if (wifiStatus == PermissionStatus.Missing) {
                                        PermissionAction.Request
                                    } else {
                                        PermissionAction.OpenAppSettings
                                    },
                            )
                    }
                val location =
                    PermissionItem(
                        id = "location_services",
                        nameRes = R.string.permissions_location_services,
                        kind = PermissionKind.SystemService,
                        status = locationStatus,
                        rationaleRes = R.string.permissions_location_rationale,
                        action =
                            if (locationStatus == PermissionStatus.Disabled) {
                                PermissionAction.OpenLocationSettings
                            } else {
                                PermissionAction.None
                            },
                    )
                return listOf(wifi, location)
            }
        }

    @Test
    fun permissionDeniedShowsMissingWithRequestAction() {
        val vm =
            PermissionCenterViewModel { denied ->
                inventory(PermissionStatus.Missing, permanentlyDenied = denied)
            }
        val wifi = vm.state.value.items.first { it.id == "wifi_discovery" }
        assertEquals(PermissionStatus.Missing, wifi.status)
        assertEquals(PermissionAction.Request, wifi.action)
    }

    @Test
    fun permanentlyDeniedOffersOpenSettings() {
        val vm =
            PermissionCenterViewModel { denied ->
                inventory(PermissionStatus.Missing, permanentlyDenied = denied)
            }
        vm.markPermanentlyDenied("NEARBY_WIFI_DEVICES")
        val wifi = vm.state.value.items.first { it.id == "wifi_discovery" }
        assertEquals(PermissionStatus.PermanentlyDenied, wifi.status)
        assertEquals(PermissionAction.OpenAppSettings, wifi.action)
    }

    @Test
    fun serviceDisabledOffersLocationSettings() {
        val vm =
            PermissionCenterViewModel {
                inventory(PermissionStatus.Granted, locationStatus = PermissionStatus.Disabled)
            }
        val location = vm.state.value.items.first { it.id == "location_services" }
        assertEquals(PermissionStatus.Disabled, location.status)
        assertEquals(PermissionAction.OpenLocationSettings, location.action)
    }

    @Test
    fun permissionRestoredClearsPermanentDenial() {
        val vm =
            PermissionCenterViewModel { denied ->
                if (denied.isEmpty()) {
                    inventory(PermissionStatus.Granted, permanentlyDenied = denied)
                } else {
                    inventory(PermissionStatus.Missing, permanentlyDenied = denied)
                }
            }
        vm.markPermanentlyDenied("NEARBY_WIFI_DEVICES")
        assertEquals(
            PermissionStatus.PermanentlyDenied,
            vm.state.value.items.first { it.id == "wifi_discovery" }.status,
        )
        vm.clearPermanentDenial("NEARBY_WIFI_DEVICES")
        val wifi = vm.state.value.items.first { it.id == "wifi_discovery" }
        assertEquals(PermissionStatus.Granted, wifi.status)
        assertEquals(PermissionAction.None, wifi.action)
        assertTrue(vm.state.value.items.none { it.status == PermissionStatus.PermanentlyDenied })
    }
}

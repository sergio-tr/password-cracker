package com.wifiauditlab.android.ui.permissions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCenterComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
                                name = "Wi‑Fi discovery",
                                kind = PermissionKind.RequiredPermission,
                                status = PermissionStatus.Granted,
                                rationale = "scan",
                                action = PermissionAction.None,
                            )
                        "NEARBY_WIFI_DEVICES" in permanentlyDenied ||
                            "ACCESS_FINE_LOCATION" in permanentlyDenied ->
                            PermissionItem(
                                id = "wifi_discovery",
                                name = "Wi‑Fi discovery",
                                kind = PermissionKind.RequiredPermission,
                                status = PermissionStatus.PermanentlyDenied,
                                rationale = "scan",
                                action = PermissionAction.OpenAppSettings,
                            )
                        else ->
                            PermissionItem(
                                id = "wifi_discovery",
                                name = "Wi‑Fi discovery",
                                kind = PermissionKind.RequiredPermission,
                                status = wifiStatus,
                                rationale = "scan",
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
                        name = "Location services",
                        kind = PermissionKind.SystemService,
                        status = locationStatus,
                        rationale = "location",
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

    private fun setContent(
        viewModel: PermissionCenterViewModel,
        onRequest: () -> Unit = {},
        onApp: () -> Unit = {},
        onLocation: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                PermissionCenterContent(
                    viewModel = viewModel,
                    onRequestPermission = onRequest,
                    onOpenAppSettings = onApp,
                    onOpenLocationSettings = onLocation,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun granted_showsGrantedStatusWithoutPrimaryAction() {
        val vm = PermissionCenterViewModel { inventory(PermissionStatus.Granted) }
        setContent(vm)
        composeTestRule.onNodeWithText("Estado: Granted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Centro de permisos").assertIsDisplayed()
    }

    @Test
    fun missing_concederCallback() {
        var requested = false
        val vm = PermissionCenterViewModel { denied -> inventory(PermissionStatus.Missing, permanentlyDenied = denied) }
        setContent(vm, onRequest = { requested = true })
        composeTestRule.onNodeWithText("Estado: Missing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Conceder").performClick()
        assertTrue(requested)
    }

    @Test
    fun permanentlyDenied_abrirAjustesCallback() {
        var openedApp = false
        val vm =
            PermissionCenterViewModel { denied ->
                inventory(PermissionStatus.Missing, permanentlyDenied = denied)
            }
        vm.markPermanentlyDenied("NEARBY_WIFI_DEVICES")
        setContent(vm, onApp = { openedApp = true })
        composeTestRule.onNodeWithText("Estado: Permanently denied").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abrir ajustes").performClick()
        assertTrue(openedApp)
    }

    @Test
    fun locationDisabled_abrirAjustesUbicacionCallback() {
        var openedLocation = false
        val vm =
            PermissionCenterViewModel {
                inventory(PermissionStatus.Granted, locationStatus = PermissionStatus.Disabled)
            }
        setContent(vm, onLocation = { openedLocation = true })
        composeTestRule.onNodeWithText("Estado: Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abrir ajustes de ubicación").performClick()
        assertTrue(openedLocation)
    }
}

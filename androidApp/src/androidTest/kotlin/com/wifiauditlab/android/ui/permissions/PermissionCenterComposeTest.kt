package com.wifiauditlab.android.ui.permissions

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCenterComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

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
                                rationaleRes = R.string.permissions_wifi_rationale_legacy,
                                action = PermissionAction.None,
                            )
                        "NEARBY_WIFI_DEVICES" in permanentlyDenied ||
                            "ACCESS_FINE_LOCATION" in permanentlyDenied ->
                            PermissionItem(
                                id = "wifi_discovery",
                                nameRes = R.string.permissions_wifi_discovery,
                                kind = PermissionKind.RequiredPermission,
                                status = PermissionStatus.PermanentlyDenied,
                                rationaleRes = R.string.permissions_wifi_rationale_legacy,
                                action = PermissionAction.OpenAppSettings,
                            )
                        else ->
                            PermissionItem(
                                id = "wifi_discovery",
                                nameRes = R.string.permissions_wifi_discovery,
                                kind = PermissionKind.RequiredPermission,
                                status = wifiStatus,
                                rationaleRes = R.string.permissions_wifi_rationale_legacy,
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

    private fun statusLabel(statusRes: Int): String =
        activity.getString(R.string.permissions_status, activity.getString(statusRes))

    @Test
    fun granted_showsGrantedStatusWithoutPrimaryAction() {
        val vm = PermissionCenterViewModel { inventory(PermissionStatus.Granted) }
        setContent(vm)
        composeTestRule
            .onNodeWithText(statusLabel(R.string.permissions_status_granted))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.permissions_title)).assertIsDisplayed()
    }

    @Test
    fun missing_concederCallback() {
        var requested = false
        val vm = PermissionCenterViewModel { denied -> inventory(PermissionStatus.Missing, permanentlyDenied = denied) }
        setContent(vm, onRequest = { requested = true })
        composeTestRule
            .onNodeWithText(statusLabel(R.string.permissions_status_missing))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.permissions_grant)).performClick()
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
        composeTestRule
            .onNodeWithText(statusLabel(R.string.permissions_status_permanently_denied))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.permissions_open_settings)).performClick()
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
        composeTestRule
            .onNodeWithText(statusLabel(R.string.permissions_status_disabled))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.permissions_open_location_settings))
            .performClick()
        assertTrue(openedLocation)
    }
}

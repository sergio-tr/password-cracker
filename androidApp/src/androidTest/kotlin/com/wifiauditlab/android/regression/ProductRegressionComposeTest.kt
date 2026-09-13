package com.wifiauditlab.android.regression

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.lab.LabScreen
import com.wifiauditlab.android.ui.lab.LabViewModel
import com.wifiauditlab.android.ui.permissions.PermissionAction
import com.wifiauditlab.android.ui.permissions.PermissionCenterContent
import com.wifiauditlab.android.ui.permissions.PermissionCenterViewModel
import com.wifiauditlab.android.ui.permissions.PermissionInventory
import com.wifiauditlab.android.ui.permissions.PermissionItem
import com.wifiauditlab.android.ui.permissions.PermissionKind
import com.wifiauditlab.android.ui.permissions.PermissionStatus
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.engine.DefaultSearchFeasibilityAnalyzer
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Product regression guard for FIX-01..04: localization packs, Material back
 * navigation, and guided Lab local-prototype defaults.
 */
@RunWith(AndroidJUnit4::class)
class ProductRegressionComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private class NoOpEngine : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> = emptyFlow()
    }

    @Test
    fun guidedLab_showsPrototypeSsidWithoutOpeningAdvanced() {
        val vm =
            LabViewModel(
                NoOpEngine(),
                DefaultSearchPlanOptimizer(),
                DefaultSearchFeasibilityAnalyzer(),
                FixedThroughputEstimator(),
                searchDispatcher = Dispatchers.Main.immediate,
            )
        composeTestRule.setContent {
            WifiAuditLabTheme {
                LabScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_guided_prototype))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_password))
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_challenge))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_start_test)).assertIsDisplayed()
    }

    @Test
    fun permissionCenter_hasNavigateBackAffordance() {
        var backPressed = false
        val inventory =
            object : PermissionInventory {
                override fun refresh(): List<PermissionItem> =
                    listOf(
                        PermissionItem(
                            id = "wifi_discovery",
                            nameRes = R.string.permissions_wifi_discovery,
                            kind = PermissionKind.RequiredPermission,
                            status = PermissionStatus.Granted,
                            rationaleRes = R.string.permissions_wifi_rationale_legacy,
                            action = PermissionAction.None,
                        ),
                    )
            }
        val vm = PermissionCenterViewModel { _ -> inventory }
        composeTestRule.setContent {
            WifiAuditLabTheme {
                PermissionCenterContent(
                    viewModel = vm,
                    onBack = { backPressed = true },
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onOpenLocationSettings = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.navigate_back))
            .assertIsDisplayed()
            .performClick()
        assertTrue(backPressed)
    }

    @Test
    fun spanishAndEnglishResourcePacks_differForNavAndLabTitles() {
        val es = localizedContext(Locale("es"))
        val en = localizedContext(Locale.ENGLISH)
        assertEquals("Cercanas", es.getString(R.string.nav_nearby))
        assertEquals("Nearby", en.getString(R.string.nav_nearby))
        assertNotEquals(es.getString(R.string.lab_title), en.getString(R.string.lab_title))
    }

    private fun localizedContext(locale: Locale): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config)
    }
}

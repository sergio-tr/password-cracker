package com.wifiauditlab.android.ui.lab

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.lab.LabModeSurfaces
import com.wifiauditlab.android.support.FakeLabModePreferences
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.port.LabModePreferences
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Critical product proof: Lab Mode OFF hides Lab surfaces; ON restores full Lab
 * (Guided + Prototype + Advanced). Turning OFF again navigates safely and drops
 * the Lab destination — without deleting Lab code from the app.
 */
@RunWith(AndroidJUnit4::class)
class LabModeVisibilityComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private class IdleEngine : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> = emptyFlow()
    }

    private fun labViewModel(): LabViewModel =
        LabViewModel(
            IdleEngine(),
            DefaultSearchPlanOptimizer(),
            object : SearchFeasibilityAnalyzer {
                override fun analyze(
                    plan: LabSearchPlan,
                    limits: SearchLimits,
                    estimator: SearchPerformanceEstimator,
                ) = SearchFeasibility(FeasibilityRating.Reasonable, 1.seconds, "ok")
            },
            FixedThroughputEstimator(),
            AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            searchDispatcher = Dispatchers.Main.immediate,
        )

    private data class ShellDestination(
        val route: String,
        val labelRes: Int,
        val icon: ImageVector,
    )

    @Composable
    private fun LabModeShell(
        prefs: LabModePreferences,
        labVm: LabViewModel,
    ) {
        val labModeEnabled by prefs.enabled.collectAsStateWithLifecycle()
        var selected by remember { mutableStateOf("nearby") }
        val showLab = LabModeSurfaces.showLabTab(labModeEnabled)
        if (!showLab && selected == "lab") {
            selected = LabModeSurfaces.SAFE_TOP_LEVEL_AFTER_LAB_HIDDEN
        }
        val destinations =
            buildList {
                add(ShellDestination("nearby", R.string.nav_nearby, Icons.Filled.Wifi))
                if (showLab) {
                    add(ShellDestination("lab", R.string.nav_lab, Icons.Filled.Science))
                }
                add(ShellDestination("settings", R.string.nav_settings, Icons.Filled.Settings))
            }

        Scaffold(
            bottomBar = {
                NavigationBar(Modifier.testTag("lab_mode_nav")) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination.route,
                            onClick = { selected = destination.route },
                            modifier = Modifier.testTag("shell_nav_${destination.route}"),
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                when (selected) {
                    "nearby" -> {
                        Text(
                            stringResource(R.string.nav_nearby),
                            modifier = Modifier.testTag("shell_nearby"),
                        )
                    }
                    "lab" -> {
                        if (showLab) {
                            LabScreen(viewModel = labVm)
                        }
                    }
                    "settings" -> {
                        Text(stringResource(R.string.settings_lab_mode))
                        Switch(
                            checked = labModeEnabled,
                            onCheckedChange = { prefs.setLabModeEnabled(it) },
                            modifier = Modifier.testTag("shell_lab_mode_switch"),
                        )
                    }
                }
            }
        }
    }

    private fun setShell(
        prefs: FakeLabModePreferences,
        labVm: LabViewModel = labViewModel(),
    ) {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                LabModeShell(prefs = prefs, labVm = labVm)
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun labModeOff_hidesTab_enableRestoresFullLab_disableReturnsToNearby() {
        val prefs = FakeLabModePreferences(initiallyEnabled = false)
        setShell(prefs)

        // launch → Lab Mode OFF → Lab tab absent
        composeTestRule.onNodeWithTag("shell_nearby").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithTag("shell_nav_lab").fetchSemanticsNodes().isEmpty(),
        )

        // Settings → enable Lab Mode
        composeTestRule.onNodeWithTag("shell_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shell_lab_mode_switch").assertIsOff().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shell_lab_mode_switch").assertIsOn()

        // Lab tab appears → open Lab → Guided + Prototype + Advanced
        composeTestRule.onNodeWithTag("shell_nav_lab").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_guided_prototype))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_mode_prototype), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_advanced_options))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_challenge))
            .performScrollTo()
            .assertIsDisplayed()

        // Lab → Settings → disable → navigate safely to Nearby → Lab gone
        composeTestRule.onNodeWithTag("shell_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shell_lab_mode_switch").assertIsOn().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shell_lab_mode_switch").assertIsOff()

        composeTestRule.onNodeWithTag("shell_nav_nearby").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shell_nearby").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithTag("shell_nav_lab").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_title))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}

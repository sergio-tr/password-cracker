package com.wifiauditlab.android.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.port.OnboardingPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private class FakePrefs(
        initial: Boolean = false,
    ) : OnboardingPreferences {
        var completed = initial
            private set

        override fun isCompleted(): Boolean = completed

        override fun markCompleted() {
            completed = true
        }
    }

    @Test
    fun firstRun_pagesContinueThroughAllThree() {
        val prefs = FakePrefs()
        val vm = OnboardingViewModel(prefs)
        var finished = false
        composeTestRule.setContent {
            WifiAuditLabTheme {
                OnboardingScreen(viewModel = vm, onFinished = { finished = true })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(activity.getString(OnboardingPage.Nearby.titleRes))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.onboarding_cd_continue))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(activity.getString(OnboardingPage.Vault.titleRes))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.onboarding_cd_continue))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(activity.getString(OnboardingPage.Lab.titleRes))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.onboarding_start)).performClick()
        composeTestRule.waitUntil(5_000) { finished && prefs.completed }
        assertTrue(finished)
        assertTrue(prefs.completed)
    }

    @Test
    fun skip_finishesOnboarding() {
        val prefs = FakePrefs()
        val vm = OnboardingViewModel(prefs)
        var finished = false
        composeTestRule.setContent {
            WifiAuditLabTheme {
                OnboardingScreen(viewModel = vm, onFinished = { finished = true })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.onboarding_cd_skip))
            .performClick()
        composeTestRule.waitUntil(5_000) { finished && prefs.completed }
        assertTrue(finished)
        assertTrue(prefs.completed)
    }

    @Test
    fun completed_doesNotShowContent_callsOnFinished() {
        val prefs = FakePrefs(initial = true)
        val vm = OnboardingViewModel(prefs)
        var finished = false
        composeTestRule.setContent {
            WifiAuditLabTheme {
                OnboardingScreen(viewModel = vm, replay = false, onFinished = { finished = true })
            }
        }
        composeTestRule.waitUntil(5_000) { finished }
        assertTrue(finished)
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.onboarding_title))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun replayFromSettings_showsContentAgain() {
        val prefs = FakePrefs(initial = true)
        val vm = OnboardingViewModel(prefs)
        var finished = false
        composeTestRule.setContent {
            WifiAuditLabTheme {
                OnboardingScreen(viewModel = vm, replay = true, onFinished = { finished = true })
            }
        }
        composeTestRule.waitForIdle()
        val nearbyTitle = activity.getString(OnboardingPage.Nearby.titleRes)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(nearbyTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(nearbyTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.onboarding_title)).assertIsDisplayed()
        assertFalse(vm.state.value.completed)
        assertTrue(prefs.completed)
        assertFalse(finished)
    }
}

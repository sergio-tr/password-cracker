package com.wifiauditlab.android.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        composeTestRule.onNodeWithText(OnboardingPage.Nearby.title).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Continuar onboarding").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(OnboardingPage.Vault.title).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Continuar onboarding").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(OnboardingPage.Lab.title).assertIsDisplayed()
        composeTestRule.onNodeWithText("Empezar").performClick()
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
        composeTestRule.onNodeWithContentDescription("Omitir onboarding").performClick()
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
            composeTestRule.onAllNodesWithText("Bienvenida").fetchSemanticsNodes().isEmpty(),
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
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(OnboardingPage.Nearby.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(OnboardingPage.Nearby.title).assertIsDisplayed()
        composeTestRule.onNodeWithText("Bienvenida").assertIsDisplayed()
        assertFalse(vm.state.value.completed)
        assertTrue(prefs.completed)
        assertFalse(finished)
    }
}

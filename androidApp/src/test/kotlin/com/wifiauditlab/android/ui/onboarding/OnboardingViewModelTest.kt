package com.wifiauditlab.android.ui.onboarding

import com.wifiauditlab.assessment.port.OnboardingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
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
    fun firstRunStartsOnFirstPageIncomplete() {
        val prefs = FakePrefs(initial = false)
        val vm = OnboardingViewModel(prefs)
        assertFalse(vm.state.value.completed)
        assertEquals(0, vm.state.value.pageIndex)
        assertEquals(OnboardingPage.Nearby, vm.state.value.page)
    }

    @Test
    fun skipMarksCompletedAndPersists() {
        val prefs = FakePrefs()
        val vm = OnboardingViewModel(prefs)
        vm.skip()
        assertTrue(vm.state.value.completed)
        assertTrue(prefs.completed)
    }

    @Test
    fun nextAdvancesUntilLastThenCompletes() {
        val prefs = FakePrefs()
        val vm = OnboardingViewModel(prefs)
        vm.next()
        assertEquals(OnboardingPage.Vault, vm.state.value.page)
        assertFalse(vm.state.value.completed)
        vm.next()
        assertEquals(OnboardingPage.Lab, vm.state.value.page)
        assertTrue(vm.state.value.isLast)
        vm.next()
        assertTrue(vm.state.value.completed)
        assertTrue(prefs.completed)
    }

    @Test
    fun completedOnboardingCanReopenFromSettings() {
        val prefs = FakePrefs(initial = true)
        val vm = OnboardingViewModel(prefs)
        assertTrue(vm.state.value.completed)
        vm.reopenFromSettings()
        assertFalse(vm.state.value.completed)
        assertEquals(0, vm.state.value.pageIndex)
        assertTrue(prefs.completed)
    }
}

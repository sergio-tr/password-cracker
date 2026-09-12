package com.wifiauditlab.lab.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchStateTransitionTest {
    @Test
    fun happy_path_idle_preparing_running_completed() {
        var state = SearchState.Idle
        state = SearchLifecycle.requireTransition(state, SearchState.Preparing)
        state = SearchLifecycle.requireTransition(state, SearchState.Running)
        state = SearchLifecycle.requireTransition(state, SearchState.Completed)
        assertEquals(SearchState.Completed, state)
    }

    @Test
    fun running_can_reach_each_terminal_except_idle() {
        val terminals =
            setOf(
                SearchState.Cancelling,
                SearchState.Completed,
                SearchState.LimitReached,
                SearchState.Failed,
                SearchState.Cancelled,
            )
        assertEquals(terminals, SearchLifecycle.allowedTargets(SearchState.Running))
    }

    @Test
    fun cancelling_only_ends_cancelled_or_failed() {
        assertTrue(SearchLifecycle.canTransition(SearchState.Running, SearchState.Cancelling))
        assertTrue(SearchLifecycle.canTransition(SearchState.Cancelling, SearchState.Cancelled))
        assertTrue(SearchLifecycle.canTransition(SearchState.Cancelling, SearchState.Failed))
        assertFalse(SearchLifecycle.canTransition(SearchState.Cancelling, SearchState.Running))
    }

    @Test
    fun terminal_states_only_reset_to_idle() {
        val terminals =
            listOf(
                SearchState.Cancelled,
                SearchState.Completed,
                SearchState.LimitReached,
                SearchState.Failed,
            )
        terminals.forEach { terminal ->
            assertEquals(setOf(SearchState.Idle), SearchLifecycle.allowedTargets(terminal))
            assertFailsWith<IllegalArgumentException> {
                SearchLifecycle.requireTransition(terminal, SearchState.Running)
            }
        }
    }

    @Test
    fun idle_cannot_skip_to_running() {
        assertFalse(SearchLifecycle.canTransition(SearchState.Idle, SearchState.Running))
        assertFailsWith<IllegalArgumentException> {
            SearchLifecycle.requireTransition(SearchState.Idle, SearchState.Completed)
        }
    }
}

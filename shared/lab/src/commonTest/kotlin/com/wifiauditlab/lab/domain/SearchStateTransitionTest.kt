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
    fun running_can_pause_or_reach_terminals() {
        val targets =
            setOf(
                SearchState.Pausing,
                SearchState.Cancelling,
                SearchState.Completed,
                SearchState.LimitReached,
                SearchState.Failed,
                SearchState.Cancelled,
            )
        assertEquals(targets, SearchLifecycle.allowedTargets(SearchState.Running))
    }

    @Test
    fun pause_path_running_pausing_paused_preparing() {
        assertTrue(SearchLifecycle.canTransition(SearchState.Running, SearchState.Pausing))
        assertTrue(SearchLifecycle.canTransition(SearchState.Pausing, SearchState.Paused))
        assertTrue(SearchLifecycle.canTransition(SearchState.Paused, SearchState.Preparing))
        assertFalse(SearchLifecycle.canTransition(SearchState.Paused, SearchState.Running))
    }

    @Test
    fun paused_is_not_cancelled() {
        assertFalse(SearchLifecycle.canTransition(SearchState.Paused, SearchState.Completed))
        assertTrue(SearchLifecycle.canTransition(SearchState.Paused, SearchState.Cancelled))
        assertTrue(SearchLifecycle.canTransition(SearchState.Paused, SearchState.Idle))
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

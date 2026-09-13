package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/** Explicit, mutually-exclusive lifecycle of a search. Avoids impossible boolean combinations. */
enum class SearchState {
    Idle,
    Preparing,
    Running,
    Pausing,
    Paused,
    Cancelling,
    Cancelled,
    Completed,
    LimitReached,
    Failed,
}

/** Allowed transitions of [SearchState]. Terminal states may only reset to [SearchState.Idle]. */
object SearchLifecycle {
    fun allowedTargets(from: SearchState): Set<SearchState> =
        when (from) {
            SearchState.Idle -> setOf(SearchState.Preparing)
            SearchState.Preparing ->
                setOf(
                    SearchState.Running,
                    SearchState.Pausing,
                    SearchState.Cancelling,
                    SearchState.Cancelled,
                    SearchState.Failed,
                )
            SearchState.Running ->
                setOf(
                    SearchState.Pausing,
                    SearchState.Cancelling,
                    SearchState.Completed,
                    SearchState.LimitReached,
                    SearchState.Failed,
                    SearchState.Cancelled,
                )
            SearchState.Pausing ->
                setOf(SearchState.Paused, SearchState.Cancelling, SearchState.Cancelled, SearchState.Failed)
            SearchState.Paused ->
                setOf(SearchState.Preparing, SearchState.Cancelling, SearchState.Cancelled, SearchState.Idle)
            SearchState.Cancelling -> setOf(SearchState.Cancelled, SearchState.Failed)
            SearchState.Cancelled,
            SearchState.Completed,
            SearchState.LimitReached,
            SearchState.Failed,
            -> setOf(SearchState.Idle)
        }

    fun canTransition(
        from: SearchState,
        to: SearchState,
    ): Boolean = to in allowedTargets(from)

    fun requireTransition(
        from: SearchState,
        to: SearchState,
    ): SearchState {
        require(canTransition(from, to)) { "illegal search transition $from -> $to" }
        return to
    }
}

/**
 * UI-oriented snapshot of an in-flight search: explicit state plus the latest
 * aggregated metrics. Never updated per candidate.
 */
data class LabSearchProgress(
    val state: SearchState,
    val metrics: SearchMetrics,
)

/** Which configured limit stopped the search. */
enum class LimitReason { Duration, Attempts }

/**
 * Aggregated, batch-level snapshot of a running or finished search. Never updated
 * per candidate — the engine emits this at most every `progressInterval`.
 */
data class SearchMetrics(
    val attempts: CombinationCount,
    val elapsed: Duration,
    val attemptsPerSecond: Double,
    val currentBucketIndex: Int,
    val totalBuckets: Int,
    val searchSpace: CombinationCount,
    val processedPercentage: Double?,
    val estimatedRemaining: Duration?,
) {
    companion object {
        fun initial(
            totalBuckets: Int,
            searchSpace: CombinationCount,
        ): SearchMetrics =
            SearchMetrics(
                attempts = CombinationCount.ZERO,
                elapsed = Duration.ZERO,
                attemptsPerSecond = 0.0,
                currentBucketIndex = 0,
                totalBuckets = totalBuckets,
                searchSpace = searchSpace,
                processedPercentage = null,
                estimatedRemaining = null,
            )
    }
}

/** Events emitted by the engine as a cold flow. Terminal events end the flow. */
sealed interface LabSearchEvent {
    data object Preparing : LabSearchEvent

    data class Started(
        val sessionId: SearchSessionId,
        val plan: LabSearchPlan,
        val searchSpace: CombinationCount,
    ) : LabSearchEvent

    data class Progress(val metrics: SearchMetrics) : LabSearchEvent

    /** Terminal: the hidden secret was found. In the synthetic lab, revealing it is the goal. */
    data class CandidateFound(val candidate: String, val metrics: SearchMetrics) : LabSearchEvent

    /** Terminal: a configured limit was reached before finding the secret. */
    data class LimitReached(val reason: LimitReason, val metrics: SearchMetrics) : LabSearchEvent

    /** Terminal: the user cancelled the search. */
    data class Cancelled(val metrics: SearchMetrics) : LabSearchEvent

    /**
     * Flow-terminal but session-resumable: cooperative pause. Distinct from
     * [Cancelled] — the checkpoint remains valid until resume or explicit stop.
     */
    data class Paused(
        val metrics: SearchMetrics,
        val cursor: LabSearchCursor,
    ) : LabSearchEvent

    /** Terminal: the whole space was exhausted without finding the secret. */
    data class Completed(val metrics: SearchMetrics) : LabSearchEvent

    /** Terminal: an unexpected error aborted the search. */
    data class Failed(val message: String, val metrics: SearchMetrics?) : LabSearchEvent
}

/** Final outcome of a search, derived from the terminal [LabSearchEvent]. */
enum class SearchOutcome { Found, NotFound, LimitReached, Cancelled, Failed }

data class LabSearchResult(
    val sessionId: SearchSessionId,
    val outcome: SearchOutcome,
    val metrics: SearchMetrics,
    val foundCandidate: String?,
    val limitReason: LimitReason? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun fromTerminal(
            sessionId: SearchSessionId,
            event: LabSearchEvent,
        ): LabSearchResult? {
            val emptyMetrics = SearchMetrics.initial(0, CombinationCount.ZERO)
            return when (event) {
                is LabSearchEvent.CandidateFound ->
                    LabSearchResult(sessionId, SearchOutcome.Found, event.metrics, event.candidate)
                is LabSearchEvent.Completed ->
                    LabSearchResult(sessionId, SearchOutcome.NotFound, event.metrics, null)
                is LabSearchEvent.LimitReached ->
                    LabSearchResult(sessionId, SearchOutcome.LimitReached, event.metrics, null, event.reason)
                is LabSearchEvent.Cancelled ->
                    LabSearchResult(sessionId, SearchOutcome.Cancelled, event.metrics, null)
                is LabSearchEvent.Failed ->
                    LabSearchResult(
                        sessionId,
                        SearchOutcome.Failed,
                        event.metrics ?: emptyMetrics,
                        null,
                        errorMessage = event.message,
                    )
                else -> null
            }
        }
    }
}

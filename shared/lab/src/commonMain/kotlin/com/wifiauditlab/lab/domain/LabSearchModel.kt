package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/** Explicit, mutually-exclusive lifecycle of a search. Avoids impossible boolean combinations. */
enum class SearchState {
    Idle,
    Preparing,
    Running,
    Cancelling,
    Cancelled,
    Completed,
    LimitReached,
    Failed,
}

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
        fun initial(totalBuckets: Int, searchSpace: CombinationCount): SearchMetrics = SearchMetrics(
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
)

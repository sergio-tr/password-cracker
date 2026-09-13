package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchCursor
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchStrategyId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/** Builds a [CandidateVerifier] bound to the challenge's hidden secret. */
fun LabChallenge.asVerifier(): CandidateVerifier = CandidateVerifier { isSolution(it) }

/** A strategy knows how to turn a challenge into an ordered [LabSearchPlan]. */
interface LabSearchStrategy {
    val id: SearchStrategyId

    fun supports(challenge: LabChallenge): Boolean

    suspend fun createPlan(
        challenge: LabChallenge,
        limits: SearchLimits,
    ): LabSearchPlan
}

/**
 * Orders a challenge's space into buckets. Different optimizers can implement
 * different priorization policies so they can be compared in the lab.
 */
interface SearchPlanOptimizer {
    fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan
}

/**
 * Optional pause / resume controls for a lab run. Defaults preserve legacy
 * cancel-only call sites.
 */
data class LabSearchRunOptions(
    val pause: PauseSignal = NeverPause,
    val resumeFrom: LabSearchCursor? = null,
)

/** Runs a plan, emitting a cold flow of [LabSearchEvent]s. Cooperatively cancelable. */
interface LabSearchEngine {
    fun run(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        cancellation: CancellationSignal,
        options: LabSearchRunOptions = LabSearchRunOptions(),
    ): Flow<LabSearchEvent>
}

/** Estimates and rates how long a search is likely to take. */
interface SearchPerformanceEstimator {
    /** Best current guess of throughput in candidates per second. */
    fun expectedThroughput(): Double

    /** Estimated wall-clock duration to exhaust [space], or null when not meaningful. */
    fun estimateDuration(space: CombinationCount): Duration?

    /** Optional refinement from a local calibration. Must not change user limits. */
    fun refine(measuredAttemptsPerSecond: Double) = Unit
}

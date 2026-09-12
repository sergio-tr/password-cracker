package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.DurationRange
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Performance estimator with a configurable baseline throughput. A real device can
 * refine [throughput] after a short local calibration run; until then a
 * conservative default is used so estimates never look artificially fast.
 */
class FixedThroughputEstimator(
    private val throughput: Double = DEFAULT_THROUGHPUT,
) : SearchPerformanceEstimator {
    init {
        require(throughput > 0.0) { "throughput must be positive" }
    }

    override fun expectedThroughput(): Double = throughput

    override fun estimateDuration(space: CombinationCount): Duration? {
        val exact = space.toLongOrNull() ?: return null // beyond Long: not meaningfully estimable
        return (exact / throughput).seconds
    }

    companion object {
        /** Deliberately conservative default (candidates/second) before calibration. */
        const val DEFAULT_THROUGHPUT: Double = 500_000.0
    }
}

/**
 * Rates a plan so the UI can refuse to present a multi-year search as normal.
 * The rating reflects the cost of exhausting the space; the active limit is
 * surfaced in the human-readable reason.
 */
class DefaultSearchFeasibilityAnalyzer : SearchFeasibilityAnalyzer {
    override fun analyze(
        plan: LabSearchPlan,
        limits: SearchLimits,
        estimator: SearchPerformanceEstimator,
    ): SearchFeasibility {
        val space = plan.searchSpace
        if (space.isZero) {
            return SearchFeasibility(FeasibilityRating.Invalid, null, "the search space is empty")
        }

        val estimated = estimator.estimateDuration(space)
        val spaceText = space.toAbbreviatedString()
        val limitText = describeLimits(limits)

        if (estimated == null) {
            return SearchFeasibility(
                FeasibilityRating.Impractical,
                null,
                "This search is impractical with the current configuration. " +
                    "The space ($spaceText combinations) cannot be exhausted; " +
                    "only the configured limit ($limitText) would ever stop it.",
            )
        }

        val rating =
            when {
                estimated <= REASONABLE_MAX -> FeasibilityRating.Reasonable
                estimated <= EXPENSIVE_MAX -> FeasibilityRating.Expensive
                else -> FeasibilityRating.Impractical
            }
        val range = DurationRange.fromPoint(estimated)
        val reason =
            if (rating == FeasibilityRating.Impractical) {
                "This search is impractical with the current configuration. " +
                    "Estimated ${range.toApproximateString()} to exhaust $spaceText combinations " +
                    "(limit: $limitText)."
            } else {
                "≈ $spaceText combinations, estimated ${range.toApproximateString()} " +
                    "at ${estimator.expectedThroughput().toLong()}/s (limit: $limitText)"
            }
        return SearchFeasibility(rating, estimated, reason, range)
    }

    private fun describeLimits(limits: SearchLimits): String {
        val parts =
            buildList {
                limits.maxDuration?.let { add("≤ $it") }
                limits.maxAttempts?.let { add("≤ ${it.toAbbreviatedString()} attempts") }
            }
        return parts.joinToString(", ")
    }

    private companion object {
        val REASONABLE_MAX: Duration = 1.minutes
        val EXPENSIVE_MAX: Duration = 1.hours
    }
}

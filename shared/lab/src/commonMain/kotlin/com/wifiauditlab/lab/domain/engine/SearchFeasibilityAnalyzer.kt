package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import kotlin.time.Duration

enum class FeasibilityRating { Reasonable, Expensive, Impractical, Invalid }

data class SearchFeasibility(
    val rating: FeasibilityRating,
    val estimatedDuration: Duration?,
    val reason: String,
)

/**
 * Rates a plan before it runs so the UI never presents a multi-year search as a
 * normal operation. Uses the performance estimator to translate space into time.
 */
interface SearchFeasibilityAnalyzer {
    fun analyze(
        plan: LabSearchPlan,
        limits: SearchLimits,
        estimator: SearchPerformanceEstimator,
    ): SearchFeasibility
}

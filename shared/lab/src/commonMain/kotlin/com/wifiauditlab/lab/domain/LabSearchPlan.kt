package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.engine.CandidateSource
import com.wifiauditlab.lab.domain.engine.OdometerCandidateSource

/**
 * One ordered slice of the search: a single candidate length together with the
 * alphabet used and pre-computed sizing/cost metadata.
 *
 * Buckets are ordered by the plan so that cheaper slices are explored first.
 */
data class SearchBucket(
    val index: Int,
    val length: Int,
    val alphabet: Alphabet,
    val expectedRelativeWeight: Double,
    val searchSpaceSize: CombinationCount,
) {
    fun candidateSource(seed: Long?): CandidateSource =
        OdometerCandidateSource(alphabet, length, seed)
}

/**
 * A concrete, ordered plan for exploring a challenge's space, produced by a
 * [com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer]. Holds the total
 * search space so estimates and feasibility checks never recompute it.
 */
class LabSearchPlan(
    val strategyId: SearchStrategyId,
    val buckets: List<SearchBucket>,
    val seed: Long?,
) {
    init { require(buckets.isNotEmpty()) { "a search plan must contain at least one bucket" } }

    val totalBuckets: Int get() = buckets.size

    val searchSpace: CombinationCount =
        buckets.fold(CombinationCount.ZERO) { acc, bucket -> acc + bucket.searchSpaceSize }

    override fun toString(): String =
        "LabSearchPlan(strategy=$strategyId, buckets=$totalBuckets, space=${searchSpace.toAbbreviatedString()})"
}

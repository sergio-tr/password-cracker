package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchBucket
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer

/**
 * Default optimizer: one bucket per candidate length, ordered from the smallest
 * space to the largest so cheaper slices are explored first. This is a neutral,
 * synthetic priorization policy — it encodes no real-credential heuristics.
 */
class DefaultSearchPlanOptimizer : SearchPlanOptimizer {

    override fun optimize(challenge: LabChallenge, strategyId: SearchStrategyId): LabSearchPlan {
        val alphabet: Alphabet = challenge.alphabet
        val sizesByLength = challenge.lengthPolicy.lengths.associateWith { length ->
            CombinationCount.alphabetPower(alphabet.size, length)
        }
        val total = sizesByLength.values.fold(CombinationCount.ZERO) { acc, size -> acc + size }

        val buckets = sizesByLength.entries
            .sortedBy { it.key } // ascending length == ascending space for a fixed alphabet
            .mapIndexed { index, (length, size) ->
                SearchBucket(
                    index = index,
                    length = length,
                    alphabet = alphabet,
                    expectedRelativeWeight = (size.percentageOf(total) ?: 0.0) / 100.0,
                    searchSpaceSize = size,
                )
            }
        return LabSearchPlan(strategyId, buckets, challenge.seed)
    }
}

/**
 * Exhaustive brute-force strategy over the challenge's alphabet and length policy.
 * It is the deterministic single-worker baseline required before any parallelism.
 */
class BruteForceSearchStrategy(
    private val optimizer: SearchPlanOptimizer = DefaultSearchPlanOptimizer(),
) : com.wifiauditlab.lab.domain.engine.LabSearchStrategy {

    override val id: SearchStrategyId = ID

    override fun supports(challenge: LabChallenge): Boolean = true

    override suspend fun createPlan(
        challenge: LabChallenge,
        limits: com.wifiauditlab.lab.domain.SearchLimits,
    ): LabSearchPlan = optimizer.optimize(challenge, id)

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("brute-force")
    }
}

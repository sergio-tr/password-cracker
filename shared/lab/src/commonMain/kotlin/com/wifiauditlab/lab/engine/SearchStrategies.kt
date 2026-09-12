package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchBucket
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer

/**
 * Shared builder: one disjoint bucket per candidate length over the challenge
 * alphabet. Strategies only decide order and scores.
 */
internal fun planFromLengths(
    challenge: LabChallenge,
    strategyId: SearchStrategyId,
    orderedLengths: List<Int>,
    probabilityOf: (length: Int, size: CombinationCount) -> Double,
    priorityOf: (length: Int, size: CombinationCount, probability: Double) -> Double,
): LabSearchPlan {
    val alphabet = challenge.alphabet
    val sizes =
        orderedLengths.associateWith { length -> CombinationCount.alphabetPower(alphabet.size, length) }
    val buckets =
        orderedLengths.mapIndexed { index, length ->
            val size = sizes.getValue(length)
            val probability = probabilityOf(length, size)
            SearchBucket(
                index = index,
                length = length,
                alphabet = alphabet,
                expectedRelativeWeight = probability,
                searchSpaceSize = size,
                relativePriority = priorityOf(length, size, probability),
                estimatedCost = size,
            )
        }
    return LabSearchPlan(strategyId, buckets, challenge.seed)
}

/** Equal priority per length; deterministic ascending length order. */
class UniformBaselineStrategy : SearchPlanOptimizer {
    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val lengths = challenge.lengthPolicy.lengths.toList()
        val uniform = 1.0 / lengths.size
        return planFromLengths(
            challenge,
            ID,
            lengths,
            probabilityOf = { _, _ -> uniform },
            priorityOf = { _, _, _ -> uniform },
        )
    }

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("uniform-baseline")
    }
}

/** Shorter lengths first; priority falls as the slice grows. */
class LengthPrioritizedStrategy : SearchPlanOptimizer {
    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val lengths = challenge.lengthPolicy.lengths.toList()
        return planFromLengths(
            challenge,
            ID,
            lengths,
            probabilityOf = { length, _ -> challenge.policy.normalizedLengthWeight(length) },
            priorityOf = { _, size, probability ->
                val cheapness = 1.0 / maxOf(size.toLongOrNull()?.toDouble() ?: Double.MAX_VALUE, 1.0)
                probability + cheapness
            },
        )
    }

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("length-prioritized")
    }
}

/**
 * Prefers cheaper symbol classes that already belong to the challenge alphabet
 * (digits before letters before the full set) when ranking length buckets.
 * The enumerator still uses the full challenge alphabet so the space stays a
 * partition — no real-credential datasets.
 */
class TieredAlphabetStrategy : SearchPlanOptimizer {
    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val lengths = challenge.lengthPolicy.lengths.toList()
        val tierBonus = alphabetTierBonus(challenge.alphabet)
        return planFromLengths(
            challenge,
            ID,
            lengths,
            probabilityOf = { length, _ -> challenge.policy.normalizedLengthWeight(length) },
            priorityOf = { _, _, probability -> probability + tierBonus },
        )
    }

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("tiered-alphabet")

        internal fun alphabetTierBonus(alphabet: Alphabet): Double {
            val symbols = alphabet.symbols
            val hasDigits = symbols.any { it.isDigit() }
            val hasLetters = symbols.any { it.isLetter() }
            return when {
                hasDigits && hasLetters -> 0.25
                hasDigits -> 0.15
                hasLetters -> 0.05
                else -> 0.0
            }
        }
    }
}

/**
 * Orders length buckets by the synthetic length prior declared on the challenge.
 * Missing weights fall back to uniform. No external frequency tables.
 */
class SyntheticProbabilityWeightedStrategy : SearchPlanOptimizer {
    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val lengths =
            challenge.lengthPolicy.lengths.sortedByDescending { challenge.policy.normalizedLengthWeight(it) }
        return planFromLengths(
            challenge,
            ID,
            lengths,
            probabilityOf = { length, _ -> challenge.policy.normalizedLengthWeight(length) },
            priorityOf = { _, _, probability -> probability },
        )
    }

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("synthetic-probability")
    }
}

/**
 * Adaptive ordering of the same synthetic space: score = declared probability /
 * estimated cost. Weights come from [PlanScoreWeights], not from the engine.
 */
class AdaptiveSyntheticStrategy(
    private val scorer: PlanScorer = PlanScorer(),
) : SearchPlanOptimizer {
    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan {
        val lengths =
            challenge.lengthPolicy.lengths.sortedByDescending { length ->
                val size = CombinationCount.alphabetPower(challenge.alphabet.size, length)
                scorer.score(challenge.policy.normalizedLengthWeight(length), size)
            }
        return planFromLengths(
            challenge,
            ID,
            lengths,
            probabilityOf = { length, _ -> challenge.policy.normalizedLengthWeight(length) },
            priorityOf = { _, size, probability -> scorer.score(probability, size) },
        )
    }

    companion object {
        val ID: SearchStrategyId = SearchStrategyId("adaptive-synthetic")
    }
}

/** Looks up a planner by strategy id. Unknown ids fall back to length-prioritized. */
class SearchStrategyRegistry(
    private val strategies: List<SearchPlanOptimizer> =
        listOf(
            UniformBaselineStrategy(),
            LengthPrioritizedStrategy(),
            TieredAlphabetStrategy(),
            SyntheticProbabilityWeightedStrategy(),
            AdaptiveSyntheticStrategy(),
        ),
) : SearchPlanOptimizer {
    private val byId: Map<SearchStrategyId, SearchPlanOptimizer> =
        buildMap {
            put(UniformBaselineStrategy.ID, strategies.first { it is UniformBaselineStrategy })
            put(LengthPrioritizedStrategy.ID, strategies.first { it is LengthPrioritizedStrategy })
            put(TieredAlphabetStrategy.ID, strategies.first { it is TieredAlphabetStrategy })
            put(SyntheticProbabilityWeightedStrategy.ID, strategies.first { it is SyntheticProbabilityWeightedStrategy })
            put(AdaptiveSyntheticStrategy.ID, strategies.first { it is AdaptiveSyntheticStrategy })
            put(BruteForceSearchStrategy.ID, strategies.first { it is LengthPrioritizedStrategy })
        }

    val ids: List<SearchStrategyId> get() = byId.keys.distinct()

    fun optimizer(id: SearchStrategyId): SearchPlanOptimizer = byId[id] ?: LengthPrioritizedStrategy()

    override fun optimize(
        challenge: LabChallenge,
        strategyId: SearchStrategyId,
    ): LabSearchPlan = optimizer(strategyId).optimize(challenge, strategyId)
}

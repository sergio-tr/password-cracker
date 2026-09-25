package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LengthPolicy

/**
 * Progressive stage policy for **synthetic** lab experiments (short secrets).
 * Product known-password audits use [WifiPskProgressiveAuditPolicy] instead.
 *
 * Stages 1–5 are mathematically disjoint (distinct alphabets or non-overlapping
 * length ranges within the same alphabet). Stage 6 expands to lower-alphanumeric
 * and **may overlap** earlier digit/lowercase stages — duplicate candidates are
 * not filtered in memory; attempt counters remain raw, not unique.
 *
 * Weights are relative integers (sum = [TOTAL_WEIGHT]); they control budget
 * allocation, not password priors from real networks.
 */
object GenericProgressiveAuditPolicy {
    const val TOTAL_WEIGHT: Int = 100

    data class StageSpec(
        val id: String,
        val alphabet: Alphabet,
        val lengthPolicy: LengthPolicy,
        val priority: Int,
        val budgetWeight: Int,
        val mayOverlapPriorStages: Boolean,
    )

    val STAGES: List<StageSpec> =
        listOf(
            StageSpec("digits-1-4", Alphabet.DIGITS, LengthPolicy(1, 4), priority = 1, budgetWeight = 15, mayOverlapPriorStages = false),
            StageSpec("digits-5-8", Alphabet.DIGITS, LengthPolicy(5, 8), priority = 2, budgetWeight = 20, mayOverlapPriorStages = false),
            StageSpec("lower-1-4", Alphabet.LOWERCASE, LengthPolicy(1, 4), priority = 3, budgetWeight = 15, mayOverlapPriorStages = false),
            StageSpec("lower-5-8", Alphabet.LOWERCASE, LengthPolicy(5, 8), priority = 4, budgetWeight = 20, mayOverlapPriorStages = false),
            StageSpec("upper-1-4", Alphabet.UPPERCASE, LengthPolicy(1, 4), priority = 5, budgetWeight = 10, mayOverlapPriorStages = false),
            StageSpec(
                "lower-alnum-4-8",
                Alphabet.LOWER_ALPHANUMERIC,
                LengthPolicy(4, 8),
                priority = 6,
                budgetWeight = 20,
                mayOverlapPriorStages = true,
            ),
        )

    init {
        require(STAGES.sumOf { it.budgetWeight } == TOTAL_WEIGHT) {
            "stage weights must sum to $TOTAL_WEIGHT"
        }
        require(STAGES.map { it.priority } == STAGES.map { it.priority }.sorted()) {
            "stages must be listed in ascending priority"
        }
    }

    fun exactSpace(
        alphabet: Alphabet,
        lengthPolicy: LengthPolicy,
    ): CombinationCount {
        var total = CombinationCount.ZERO
        for (length in lengthPolicy.lengths) {
            total += CombinationCount.alphabetPower(alphabet.size, length)
        }
        return total
    }
}

/**
 * Splits a global attempt/duration budget across stages by relative weights.
 * The last stage receives any remainder so the sum equals the global budget.
 */
object StageBudgetAllocator {
    fun allocateAttempts(
        total: CombinationCount?,
        weights: List<Int>,
    ): List<CombinationCount?> {
        if (total == null) return List(weights.size) { null }
        require(weights.isNotEmpty())
        require(weights.all { it > 0 })
        val weightSum = weights.sum()
        val exact = total.toLongOrNull()
        if (exact == null) {
            // Beyond Long: proportional division via CombinationCount / Int.
            val parts = weights.map { total * it.toLong() / weightSum }
            val assigned = parts.fold(CombinationCount.ZERO) { acc, c -> acc + c }
            val remainder = total - assigned
            return parts.mapIndexed { index, part ->
                if (index == parts.lastIndex) part + remainder else part
            }
        }
        var remaining = exact
        return weights.mapIndexed { index, weight ->
            if (index == weights.lastIndex) {
                CombinationCount.of(remaining)
            } else {
                val share = (exact * weight) / weightSum
                remaining -= share
                CombinationCount.of(share)
            }
        }
    }

    fun allocateDurationFractions(weights: List<Int>): List<Double> {
        val sum = weights.sum().toDouble()
        return weights.map { it / sum }
    }
}

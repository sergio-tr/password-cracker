package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount

/**
 * Declared generation rules of a synthetic challenge: which symbols may appear
 * and which lengths the hidden secret is drawn from. Completely local to the lab;
 * it never encodes heuristics about real-world credentials.
 */
data class LabSecretPolicy(
    val alphabet: Alphabet,
    val length: LengthPolicy,
    val syntheticLengthWeights: Map<Int, Double> = emptyMap(),
) {
    val searchSpace: CombinationCount
        get() =
            length.lengths.fold(CombinationCount.ZERO) { acc, candidateLength ->
                acc + CombinationCount.alphabetPower(alphabet.size, candidateLength)
            }

    /** Declared synthetic prior for [candidateLength], or uniform when omitted. */
    fun lengthWeight(candidateLength: Int): Double {
        val declared = syntheticLengthWeights[candidateLength]
        if (declared != null) return declared
        return 1.0 / length.lengths.count()
    }

    fun normalizedLengthWeight(candidateLength: Int): Double {
        val total = length.lengths.sumOf { lengthWeight(it) }
        return if (total == 0.0) 0.0 else lengthWeight(candidateLength) / total
    }

    override fun toString(): String = "LabSecretPolicy(alphabet=$alphabet, length=$length)"
}

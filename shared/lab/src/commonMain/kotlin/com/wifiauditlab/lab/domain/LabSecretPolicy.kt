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
) {
    val searchSpace: CombinationCount
        get() =
            length.lengths.fold(CombinationCount.ZERO) { acc, candidateLength ->
                acc + CombinationCount.alphabetPower(alphabet.size, candidateLength)
            }

    override fun toString(): String = "LabSecretPolicy(alphabet=$alphabet, length=$length)"
}

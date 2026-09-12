package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet

/**
 * Descriptor of a fixed-length candidate slice. It never materializes the
 * strings: [size] is exact arithmetic and [source] yields a lazy odometer.
 */
data class CandidateSpace(
    val alphabet: Alphabet,
    val length: Int,
    val size: CombinationCount = CombinationCount.alphabetPower(alphabet.size, length),
) {
    init {
        require(length >= 1) { "candidate space length must be >= 1, was $length" }
    }

    fun source(seed: Long? = null): CandidateSource = OdometerCandidateSource(alphabet, length, seed)
}

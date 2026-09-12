package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import kotlin.random.Random

/**
 * Lazy odometer over the space of fixed-[length] strings drawn from [alphabet].
 *
 * Enumeration order follows the alphabet symbol order. When a [seed] is provided
 * the symbol order is deterministically permuted, allowing reproducible but
 * different priorization orders to be compared in the synthetic lab.
 *
 * Memory footprint is O([length]); the space itself is never materialized.
 */
class OdometerCandidateSource(
    alphabet: Alphabet,
    private val length: Int,
    seed: Long? = null,
) : CandidateSource {
    private val order: CharArray = buildOrder(alphabet, seed)

    override val size: CombinationCount = CombinationCount.alphabetPower(alphabet.size, length)

    override fun candidates(): Sequence<String> =
        sequence {
            if (length == 0) return@sequence
            val base = order.size
            val indices = IntArray(length)
            val buffer = CharArray(length)
            while (true) {
                for (i in 0 until length) buffer[i] = order[indices[i]]
                yield(buffer.concatToString())

                var pos = length - 1
                while (pos >= 0) {
                    indices[pos]++
                    if (indices[pos] < base) break
                    indices[pos] = 0
                    pos--
                }
                if (pos < 0) break
            }
        }

    private companion object {
        fun buildOrder(
            alphabet: Alphabet,
            seed: Long?,
        ): CharArray {
            val chars = CharArray(alphabet.size) { alphabet[it] }
            if (seed == null) return chars
            val random = Random(seed)
            // Fisher–Yates: deterministic permutation for a given seed.
            for (i in chars.indices.reversed()) {
                val j = random.nextInt(i + 1)
                val tmp = chars[i]
                chars[i] = chars[j]
                chars[j] = tmp
            }
            return chars
        }
    }
}

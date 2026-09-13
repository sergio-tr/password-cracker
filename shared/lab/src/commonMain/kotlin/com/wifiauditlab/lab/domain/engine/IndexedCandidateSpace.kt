package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import kotlin.random.Random

/**
 * Random-access view of a fixed-length candidate slice. Indexing uses base-N
 * encoding over the (optionally seeded) alphabet order so workers can jump into
 * a range without enumerating prior candidates.
 *
 * Supports spaces larger than 64 bits via [CombinationCount].
 */
interface IndexedCandidateSpace {
    val size: CombinationCount

    fun candidateAt(index: CombinationCount): String

    /**
     * Lazy enumeration of `[startInclusive, endExclusive)` that jumps to the
     * start via base-N decode and then advances like an odometer (O(length)
     * per step, no per-candidate division after the jump).
     */
    fun candidatesInRange(
        startInclusive: CombinationCount,
        endExclusive: CombinationCount,
    ): Sequence<String>
}

/**
 * Indexed twin of [OdometerCandidateSource]: same alphabet order and seed
 * permutation, plus O(1)/O(length) random access by index.
 */
class OdometerIndexedCandidateSpace(
    alphabet: Alphabet,
    private val length: Int,
    seed: Long? = null,
) : IndexedCandidateSpace {
    private val order: CharArray = buildOrder(alphabet, seed)
    private val base: Int = order.size

    override val size: CombinationCount = CombinationCount.alphabetPower(base, length)

    init {
        require(length >= 1) { "indexed candidate space length must be >= 1, was $length" }
        require(base >= 1) { "alphabet must contain at least one symbol" }
    }

    override fun candidateAt(index: CombinationCount): String {
        require(index >= CombinationCount.ZERO) { "index must be non-negative" }
        require(index < size) { "index $index out of bounds for size $size" }
        val digits = IntArray(length)
        decodeInto(index, digits)
        return String(CharArray(length) { order[digits[it]] })
    }

    override fun candidatesInRange(
        startInclusive: CombinationCount,
        endExclusive: CombinationCount,
    ): Sequence<String> =
        sequence {
            require(startInclusive >= CombinationCount.ZERO) { "start must be non-negative" }
            require(endExclusive <= size) { "endExclusive must be <= size" }
            require(startInclusive <= endExclusive) { "start must be <= endExclusive" }
            if (startInclusive == endExclusive) return@sequence

            val digits = IntArray(length)
            decodeInto(startInclusive, digits)
            val buffer = CharArray(length)
            var remaining =
                endExclusive - startInclusive
            val remainingLong = remaining.toLongOrNull()
            if (remainingLong != null) {
                var left = remainingLong
                while (left > 0L) {
                    for (i in 0 until length) buffer[i] = order[digits[i]]
                    yield(buffer.concatToString())
                    if (!increment(digits)) break
                    left--
                }
            } else {
                while (remaining > CombinationCount.ZERO) {
                    for (i in 0 until length) buffer[i] = order[digits[i]]
                    yield(buffer.concatToString())
                    if (!increment(digits)) break
                    remaining -= CombinationCount.ONE
                }
            }
        }

    private fun decodeInto(
        index: CombinationCount,
        digits: IntArray,
    ) {
        var cursor = index
        for (pos in length - 1 downTo 0) {
            digits[pos] = (cursor % base).toLongOrNull()?.toInt()
                ?: error("digit out of Int range for base $base")
            cursor /= base
        }
    }

    private fun increment(digits: IntArray): Boolean {
        var pos = length - 1
        while (pos >= 0) {
            digits[pos]++
            if (digits[pos] < base) return true
            digits[pos] = 0
            pos--
        }
        return false
    }

    private companion object {
        fun buildOrder(
            alphabet: Alphabet,
            seed: Long?,
        ): CharArray {
            val chars = CharArray(alphabet.size) { alphabet[it] }
            if (seed == null) return chars
            val random = Random(seed)
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

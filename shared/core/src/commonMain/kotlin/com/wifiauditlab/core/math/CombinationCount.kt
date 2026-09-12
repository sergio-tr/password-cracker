package com.wifiauditlab.core.math

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Exact, arbitrary-precision count of combinations in a search space.
 *
 * The size of a search space can exceed 64 bits, so neither [Int], [Long] nor
 * [Double] are acceptable representations. This type wraps a multiplatform
 * big-integer so the rest of the domain never depends on the concrete library.
 */
class CombinationCount private constructor(internal val value: BigInteger) : Comparable<CombinationCount> {

    val isZero: Boolean get() = value.isZero()

    operator fun plus(other: CombinationCount): CombinationCount = CombinationCount(value + other.value)

    operator fun times(other: CombinationCount): CombinationCount = CombinationCount(value * other.value)

    operator fun times(factor: Long): CombinationCount = CombinationCount(value * BigInteger.fromLong(factor))

    /** Raises this count to [exponent] (exponent >= 0). */
    fun pow(exponent: Int): CombinationCount {
        require(exponent >= 0) { "exponent must be non-negative, was $exponent" }
        return CombinationCount(value.pow(exponent))
    }

    override fun compareTo(other: CombinationCount): Int = value.compareTo(other.value)

    /**
     * Percentage that this count represents of [total] as a Double in `[0, 100+]`,
     * or `null` when [total] is zero (percentage is undefined).
     *
     * The exact values are preserved internally; only the returned ratio is lossy.
     */
    fun percentageOf(total: CombinationCount): Double? {
        if (total.isZero) return null
        val scaled = (value * HUNDRED_SCALED) / total.value
        return scaled.longValue(exactRequired = false) / 100.0
    }

    /** Fits into a [Long] without overflow. */
    fun fitsInLong(): Boolean = value <= LONG_MAX && value >= BigInteger.ZERO

    fun toLongOrNull(): Long? = if (fitsInLong()) value.longValue(exactRequired = true) else null

    /** Exact decimal representation grouped in thousands, e.g. `2,300,000`. */
    fun toExactString(): String = groupThousands(value.toString(10))

    /**
     * Human-friendly, abbreviated representation that never lies about magnitude:
     * `12,340`, `8.2 M`, `17.4 B`, `2.3 × 10^24`.
     * The exact value is always available through [toExactString].
     */
    fun toAbbreviatedString(): String {
        if (value < BigInteger.ZERO) return "-" + CombinationCount(value.negate()).toAbbreviatedString()
        val digits = if (value.isZero()) "0" else value.toString(10)
        val n = digits.length
        return when {
            value < MILLION -> groupThousands(digits)
            n <= 9 -> scaled(digits, scaleExp = 6, suffix = "M")
            n <= 12 -> scaled(digits, scaleExp = 9, suffix = "B")
            n <= 15 -> scaled(digits, scaleExp = 12, suffix = "T")
            else -> scientific(digits)
        }
    }

    override fun equals(other: Any?): Boolean = other is CombinationCount && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = toExactString()

    companion object {
        val ZERO: CombinationCount = CombinationCount(BigInteger.ZERO)
        val ONE: CombinationCount = CombinationCount(BigInteger.ONE)

        private val MILLION = BigInteger.fromInt(1_000_000)
        private val HUNDRED_SCALED = BigInteger.fromInt(10_000)
        private val LONG_MAX = BigInteger.fromLong(Long.MAX_VALUE)

        fun of(count: Long): CombinationCount {
            require(count >= 0) { "combination count must be non-negative, was $count" }
            return CombinationCount(BigInteger.fromLong(count))
        }

        fun of(decimal: String): CombinationCount {
            val parsed = BigInteger.parseString(decimal.trim(), base = 10)
            require(parsed >= BigInteger.ZERO) { "combination count must be non-negative, was $decimal" }
            return CombinationCount(parsed)
        }

        /** `base ^ length`, the size of the space of strings of [length] over an alphabet of [base] symbols. */
        fun alphabetPower(base: Int, length: Int): CombinationCount {
            require(base >= 0) { "alphabet size must be non-negative, was $base" }
            require(length >= 0) { "length must be non-negative, was $length" }
            return CombinationCount(BigInteger.fromInt(base).pow(length))
        }

        private fun groupThousands(digits: String): String {
            val negative = digits.startsWith("-")
            val body = if (negative) digits.substring(1) else digits
            val sb = StringBuilder()
            val firstGroup = body.length % 3
            for (i in body.indices) {
                if (i != 0 && (i - firstGroup) % 3 == 0) sb.append(',')
                sb.append(body[i])
            }
            return if (negative) "-$sb" else sb.toString()
        }

        private fun scaled(digits: String, scaleExp: Int, suffix: String): String {
            val intLen = digits.length - scaleExp
            val intPart = groupThousands(digits.substring(0, intLen))
            val fracDigit = digits[intLen]
            return if (fracDigit == '0') "$intPart $suffix" else "$intPart.$fracDigit $suffix"
        }

        private fun scientific(digits: String): String {
            val mantissaInt = digits[0]
            val mantissaFrac = digits[1]
            val exponent = digits.length - 1
            return if (mantissaFrac == '0') "$mantissaInt × 10^$exponent" else "$mantissaInt.$mantissaFrac × 10^$exponent"
        }
    }
}

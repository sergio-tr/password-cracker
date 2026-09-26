package com.wifiauditlab.lab.domain

/**
 * A finite, ordered set of distinct symbols used to build synthetic candidates.
 *
 * The order of symbols is meaningful: it defines the deterministic enumeration
 * order of the candidate space (before any seed-based permutation is applied).
 */
class Alphabet private constructor(val symbols: String) {
    val size: Int get() = symbols.length

    operator fun get(index: Int): Char = symbols[index]

    override fun equals(other: Any?): Boolean = other is Alphabet && symbols == other.symbols

    override fun hashCode(): Int = symbols.hashCode()

    /** Never dumps the raw symbol string verbatim to avoid noisy logs; reports size only. */
    override fun toString(): String = "Alphabet(size=$size)"

    companion object {
        fun of(symbols: String): Alphabet {
            require(symbols.isNotEmpty()) { "alphabet must contain at least one symbol" }
            require(symbols.toSet().size == symbols.length) { "alphabet symbols must be distinct" }
            return Alphabet(symbols)
        }

        val DIGITS: Alphabet = of("0123456789")
        val LOWERCASE: Alphabet = of("abcdefghijklmnopqrstuvwxyz")
        val UPPERCASE: Alphabet = of("ABCDEFGHIJKLMNOPQRSTUVWXYZ")

        /** Mixed-case letters only (a-z + A-Z), no digits. */
        val LETTERS: Alphabet = of(LOWERCASE.symbols + UPPERCASE.symbols)
        val LOWER_ALPHANUMERIC: Alphabet = of(LOWERCASE.symbols + DIGITS.symbols)
        val ALPHANUMERIC: Alphabet = of(LOWERCASE.symbols + UPPERCASE.symbols + DIGITS.symbols)

        /** Uppercase hex 0-9A-F — WEP key material (40/104-bit hex entry). */
        val HEX_UPPER: Alphabet = of("0123456789ABCDEF")

        /** Lowercase hex 0-9a-f — alternate WEP hex entry casing. */
        val HEX_LOWER: Alphabet = of("0123456789abcdef")

        /** Printable ASCII 0x20–0x7E — valid Wi‑Fi PSK passphrase charset (inclusive). */
        val PRINTABLE_ASCII: Alphabet = of((32..126).map { it.toChar() }.joinToString(""))

        /** Common punctuation symbols valid in Wi‑Fi PSK passphrases (subset of [PRINTABLE_ASCII]). */
        val WIFI_PSK_SYMBOLS: Alphabet =
            of("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")
    }
}

/**
 * The inclusive range of candidate lengths a search must cover.
 * A challenge with lengths `4..6` produces buckets for lengths 4, 5 and 6.
 */
data class LengthPolicy(val minLength: Int, val maxLength: Int) {
    init {
        require(minLength >= 1) { "minLength must be >= 1, was $minLength" }
        require(maxLength >= minLength) { "maxLength ($maxLength) must be >= minLength ($minLength)" }
    }

    val lengths: IntRange get() = minLength..maxLength

    companion object {
        fun exactly(length: Int): LengthPolicy = LengthPolicy(length, length)
    }
}

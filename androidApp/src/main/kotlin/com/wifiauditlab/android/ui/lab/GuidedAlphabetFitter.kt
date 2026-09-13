package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.lab.domain.Alphabet

/**
 * Picks the smallest alphabet preset that covers every character in [password],
 * expanding in order: digits → lowercase → mixed case → symbols (custom union).
 */
object GuidedAlphabetFitter {
    data class FitResult(
        val choice: AlphabetChoice,
        val customAlphabet: Alphabet? = null,
    )

    fun fit(password: String): FitResult? {
        if (password.isEmpty()) return null

        if (password.all { Alphabet.DIGITS.symbols.contains(it) }) {
            return FitResult(AlphabetChoice.DIGITS)
        }
        if (password.all { Alphabet.LOWERCASE.symbols.contains(it) }) {
            return FitResult(AlphabetChoice.LOWERCASE)
        }
        if (password.all { Alphabet.LOWER_ALPHANUMERIC.symbols.contains(it) }) {
            return FitResult(AlphabetChoice.LOWER_ALPHANUMERIC)
        }
        if (password.all { Alphabet.ALPHANUMERIC.symbols.contains(it) }) {
            return FitResult(AlphabetChoice.ALPHANUMERIC)
        }

        return FitResult(AlphabetChoice.ALPHANUMERIC, customAlphabet = expandedAlphabet(password))
    }

    private fun expandedAlphabet(password: String): Alphabet {
        val symbols = LinkedHashSet<Char>()
        if (password.any { it.isDigit() }) {
            Alphabet.DIGITS.symbols.forEach { symbols.add(it) }
        }
        if (password.any { it.isLowerCase() }) {
            Alphabet.LOWERCASE.symbols.forEach { symbols.add(it) }
        }
        if (password.any { it.isUpperCase() }) {
            Alphabet.UPPERCASE.symbols.forEach { symbols.add(it) }
        }
        password.filter { !it.isLetterOrDigit() }.forEach { symbols.add(it) }
        require(symbols.isNotEmpty()) { "password must contain at least one symbol" }
        return Alphabet.of(symbols.joinToString(""))
    }
}

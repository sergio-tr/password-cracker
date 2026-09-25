package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.audit.WepHexProgressiveAuditPolicy

/**
 * Picks the smallest alphabet preset that covers every character in [password],
 * expanding in order: digits → lowercase → mixed case → symbols (custom union).
 *
 * [fitForWifiPsk] restricts suggestions to [Alphabet.PRINTABLE_ASCII] (Wi‑Fi PSK
 * passphrase charset). [fitForWepHex] accepts only hex keys of length 10 or 26.
 * The fitter only assists Advanced/config alphabet suggestion and guided UI
 * feedback; guided search space still comes from auth-aware progressive
 * policies via the automatic planner (target-blind).
 */
object GuidedAlphabetFitter {
    data class FitResult(
        val choice: AlphabetChoice,
        val customAlphabet: Alphabet? = null,
    )

    /**
     * Fits [password] for Wi‑Fi PSK prototype / audit UI. Returns null when any character
     * falls outside printable ASCII (0x20–0x7E).
     */
    fun fitForWifiPsk(password: String): FitResult? {
        if (password.isEmpty()) return null
        if (!password.all { Alphabet.PRINTABLE_ASCII.symbols.contains(it) }) {
            return null
        }
        return fitWithinPrintable(password)
    }

    /**
     * Fits [password] for WEP hex-key prototype / audit UI.
     * Returns null when length is not 10/26 or any character is outside hex.
     */
    fun fitForWepHex(password: String): FitResult? {
        if (!WepHexProgressiveAuditPolicy.isValidHexKey(password)) return null
        val alphabet =
            if (password.any { it in 'a'..'f' } && password.none { it in 'A'..'F' }) {
                Alphabet.HEX_LOWER
            } else {
                Alphabet.HEX_UPPER
            }
        return FitResult(AlphabetChoice.DIGITS, customAlphabet = alphabet)
    }

    /** Fits [password] for synthetic RandomHidden exercises (no PSK charset restriction). */
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

    private fun fitWithinPrintable(password: String): FitResult? {
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

        return FitResult(AlphabetChoice.ALPHANUMERIC, customAlphabet = expandedPrintableAlphabet(password))
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

    private fun expandedPrintableAlphabet(password: String): Alphabet {
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
        password.filter { !it.isLetterOrDigit() && Alphabet.PRINTABLE_ASCII.symbols.contains(it) }
            .forEach { symbols.add(it) }
        require(symbols.isNotEmpty()) { "password must contain at least one printable symbol" }
        return Alphabet.of(symbols.joinToString(""))
    }
}

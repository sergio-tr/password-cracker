package com.wifiauditlab.android.ui.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedAlphabetFitterTest {
    @Test
    fun fitForWifiPsk_digitsPassword() {
        val fit = GuidedAlphabetFitter.fitForWifiPsk("12345678")
        assertEquals(AlphabetChoice.DIGITS, fit?.choice)
        assertNull(fit?.customAlphabet)
    }

    @Test
    fun fitForWifiPsk_printableWithSymbols() {
        val fit = GuidedAlphabetFitter.fitForWifiPsk("pass!word")
        assertEquals(AlphabetChoice.ALPHANUMERIC, fit?.choice)
        assertEquals('!', fit?.customAlphabet?.symbols?.firstOrNull { it == '!' })
    }

    @Test
    fun fitForWifiPsk_rejectsNonPrintable() {
        assertNull(GuidedAlphabetFitter.fitForWifiPsk("pass\u0001word"))
        assertNull(GuidedAlphabetFitter.fitForWifiPsk("café1234"))
    }

    @Test
    fun fit_allowsNonPrintableForSynthetic() {
        val fit = GuidedAlphabetFitter.fit("abc\u0001")
        assertEquals(AlphabetChoice.ALPHANUMERIC, fit?.choice)
        assertEquals('\u0001', fit?.customAlphabet?.symbols?.firstOrNull { it == '\u0001' })
    }
}

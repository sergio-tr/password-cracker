package com.wifiauditlab.lab.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AlphabetTest {
    @Test
    fun of_rejects_empty_and_duplicate_symbols() {
        assertFailsWith<IllegalArgumentException> { Alphabet.of("") }
        assertFailsWith<IllegalArgumentException> { Alphabet.of("aab") }
    }

    @Test
    fun presets_have_expected_sizes_and_ordering() {
        assertEquals(10, Alphabet.DIGITS.size)
        assertEquals(26, Alphabet.LOWERCASE.size)
        assertEquals(26, Alphabet.UPPERCASE.size)
        assertEquals(36, Alphabet.LOWER_ALPHANUMERIC.size)
        assertEquals(62, Alphabet.ALPHANUMERIC.size)
        assertEquals('0', Alphabet.DIGITS[0])
        assertEquals('9', Alphabet.DIGITS[9])
    }

    @Test
    fun toString_does_not_dump_symbols() {
        assertEquals("Alphabet(size=10)", Alphabet.DIGITS.toString())
        assertTrue(!Alphabet.LOWERCASE.toString().contains("abc"))
    }

    @Test
    fun equality_is_by_symbol_string() {
        assertEquals(Alphabet.of("abc"), Alphabet.of("abc"))
        assertEquals(Alphabet.of("abc").hashCode(), Alphabet.of("abc").hashCode())
        assertTrue(Alphabet.of("abc") != Alphabet.of("cba"))
    }

    @Test
    fun length_policy_validates_range_and_exposes_lengths() {
        assertFailsWith<IllegalArgumentException> { LengthPolicy(0, 3) }
        assertFailsWith<IllegalArgumentException> { LengthPolicy(4, 3) }
        assertEquals(4..6, LengthPolicy(4, 6).lengths)
        assertEquals(LengthPolicy(5, 5), LengthPolicy.exactly(5))
    }
}

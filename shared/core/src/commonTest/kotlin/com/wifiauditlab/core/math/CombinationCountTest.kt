package com.wifiauditlab.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CombinationCountTest {
    @Test
    fun exact_string_groups_thousands() {
        assertEquals("12,340", CombinationCount.of(12_340).toExactString())
        assertEquals("512", CombinationCount.of(512).toExactString())
        assertEquals("1,000,000", CombinationCount.of(1_000_000).toExactString())
    }

    @Test
    fun abbreviation_uses_grouping_below_one_million() {
        assertEquals("12,340", CombinationCount.of(12_340).toAbbreviatedString())
    }

    @Test
    fun abbreviation_uses_suffixes() {
        assertEquals("8.2 M", CombinationCount.of(8_200_000).toAbbreviatedString())
        assertEquals("17.4 B", CombinationCount.of(17_400_000_000).toAbbreviatedString())
        assertEquals("3 M", CombinationCount.of(3_000_000).toAbbreviatedString())
    }

    @Test
    fun abbreviation_uses_scientific_notation_beyond_64_bits() {
        // 2.3 × 10^24 — far beyond Long.MAX_VALUE (~9.2 × 10^18)
        val huge = CombinationCount.of("2300000000000000000000000")
        assertEquals("2.3 × 10^24", huge.toAbbreviatedString())
    }

    @Test
    fun alphabet_power_matches_exponentiation() {
        // 95 printable ASCII symbols, length 8
        val space = CombinationCount.alphabetPower(base = 95, length = 8)
        assertEquals(CombinationCount.of("6634204312890625"), space)
    }

    @Test
    fun power_and_multiplication_exceed_64_bits_exactly() {
        val big = CombinationCount.alphabetPower(base = 2, length = 100) // 2^100
        assertEquals("1,267,650,600,228,229,401,496,703,205,376", big.toExactString())
        assertFalse(big.fitsInLong())
        assertNull(big.toLongOrNull())
    }

    @Test
    fun exponents_far_beyond_64_bits_stay_exact() {
        val twoTo256 = CombinationCount.alphabetPower(base = 2, length = 256)
        assertEquals(
            "115,792,089,237,316,195,423,570,985,008,687,907,853,269,984,665,640,564,039,457,584,007,913,129,639,936",
            twoTo256.toExactString(),
        )
        assertFalse(twoTo256.fitsInLong())

        val printableLen20 = CombinationCount.alphabetPower(base = 95, length = 20)
        assertEquals(
            CombinationCount.alphabetPower(95, 10) * CombinationCount.alphabetPower(95, 10),
            printableLen20,
        )
        assertFalse(printableLen20.fitsInLong())
    }

    @Test
    fun addition_and_comparison() {
        val a = CombinationCount.of(10)
        val b = CombinationCount.of(32)
        assertEquals(CombinationCount.of(42), a + b)
        assertTrue(a < b)
        assertTrue(b > a)
        assertEquals(0, a.compareTo(CombinationCount.of(10)))
    }

    @Test
    fun times_by_scalar() {
        assertEquals(CombinationCount.of(2_000), CombinationCount.of(1_000) * 2L)
    }

    @Test
    fun subtraction_division_and_remainder() {
        assertEquals(CombinationCount.of(10), CombinationCount.of(42) - CombinationCount.of(32))
        assertEquals(CombinationCount.of(43), CombinationCount.of(42) + 1L)
        assertEquals(CombinationCount.of(12), CombinationCount.of(100) / 8)
        assertEquals(CombinationCount.of(4), CombinationCount.of(100) % 8)
        assertEquals(CombinationCount.of(5), CombinationCount.of(10).coerceAtMost(CombinationCount.of(5)))
        val huge = CombinationCount.of("100000000000000000000")
        assertEquals(CombinationCount.of("10000000000000000000"), huge / 10)
        assertEquals(CombinationCount.ZERO, huge % 10)
    }

    @Test
    fun percentage_is_null_when_total_is_zero() {
        assertNull(CombinationCount.of(5).percentageOf(CombinationCount.ZERO))
    }

    @Test
    fun percentage_of_total() {
        val processed = CombinationCount.of(214)
        val total = CombinationCount.of(1_000)
        assertEquals(21.4, processed.percentageOf(total))
    }

    @Test
    fun percentage_survives_huge_totals() {
        val processed = CombinationCount.alphabetPower(2, 99) // half of 2^100
        val total = CombinationCount.alphabetPower(2, 100)
        assertEquals(50.0, processed.percentageOf(total))
    }

    @Test
    fun zero_and_one_constants() {
        assertTrue(CombinationCount.ZERO.isZero)
        assertFalse(CombinationCount.ONE.isZero)
        assertEquals("0", CombinationCount.ZERO.toAbbreviatedString())
    }
}

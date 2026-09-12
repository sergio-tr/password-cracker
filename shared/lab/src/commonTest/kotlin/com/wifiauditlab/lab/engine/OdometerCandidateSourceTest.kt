package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.engine.OdometerCandidateSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OdometerCandidateSourceTest {
    @Test
    fun enumerates_the_whole_fixed_length_space_in_order() {
        val source = OdometerCandidateSource(Alphabet.DIGITS, length = 2)
        val all = source.candidates().toList()

        assertEquals(100, all.size)
        assertEquals(CombinationCount.of(100), source.size)
        assertEquals(listOf("00", "01", "02"), all.take(3))
        assertEquals("99", all.last())
    }

    @Test
    fun is_lazy_and_incremental() {
        val source = OdometerCandidateSource(Alphabet.ALPHANUMERIC, length = 8)
        // The space is enormous; taking a handful must be instant and not allocate it all.
        val firstFew = source.candidates().take(5).toList()
        assertEquals(5, firstFew.size)
    }

    @Test
    fun seed_gives_deterministic_but_different_ordering() {
        val natural = OdometerCandidateSource(Alphabet.DIGITS, length = 1).candidates().toList()
        val seededA = OdometerCandidateSource(Alphabet.DIGITS, length = 1, seed = 42).candidates().toList()
        val seededB = OdometerCandidateSource(Alphabet.DIGITS, length = 1, seed = 42).candidates().toList()

        assertEquals(seededA, seededB) // reproducible for a given seed
        assertEquals(natural.toSet(), seededA.toSet()) // same content
        assertNotEquals(natural, seededA) // different order
    }

    @Test
    fun size_matches_alphabet_power() {
        val source = OdometerCandidateSource(Alphabet.LOWERCASE, length = 4)
        assertEquals(CombinationCount.alphabetPower(26, 4), source.size)
        assertTrue(source.size == CombinationCount.of(456_976))
    }
}

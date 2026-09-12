package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.engine.CandidateSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandidateSpaceTest {
    @Test
    fun size_is_exact_and_source_has_no_duplicates() {
        val space = CandidateSpace(Alphabet.DIGITS, length = 2)
        assertEquals(CombinationCount.of(100), space.size)
        val all = space.source().candidates().toList()
        assertEquals(100, all.size)
        assertEquals(100, all.toSet().size)
        assertEquals("00", all.first())
        assertEquals("99", all.last())
    }

    @Test
    fun seeded_source_is_a_permutation_of_the_same_space() {
        val space = CandidateSpace(Alphabet.of("ab"), length = 2)
        val natural = space.source().candidates().toList()
        val seeded = space.source(seed = 3).candidates().toList()
        assertEquals(natural.toSet(), seeded.toSet())
        assertEquals(4, seeded.size)
    }

    @Test
    fun lazy_take_does_not_require_materializing_the_space() {
        val space = CandidateSpace(Alphabet.ALPHANUMERIC, length = 12)
        assertTrue(space.size.fitsInLong().not())
        assertEquals(3, space.source().candidates().take(3).count())
    }
}

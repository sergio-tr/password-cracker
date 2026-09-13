package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.engine.OdometerCandidateSource
import com.wifiauditlab.lab.domain.engine.OdometerIndexedCandidateSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndexedCandidateSpaceTest {
    @Test
    fun candidateAt_matches_odometer_enumeration_order() {
        val alphabet = Alphabet.DIGITS
        val length = 2
        val sequential = OdometerCandidateSource(alphabet, length).candidates().toList()
        val indexed = OdometerIndexedCandidateSpace(alphabet, length)
        assertEquals(CombinationCount.of(100), indexed.size)
        sequential.forEachIndexed { i, expected ->
            assertEquals(expected, indexed.candidateAt(CombinationCount.of(i.toLong())))
        }
    }

    @Test
    fun seeded_order_matches_odometer_source() {
        val alphabet = Alphabet.of("ab")
        val seed = 7L
        val sequential = OdometerCandidateSource(alphabet, 2, seed).candidates().toList()
        val indexed = OdometerIndexedCandidateSpace(alphabet, 2, seed)
        assertEquals(sequential, (0 until sequential.size).map { indexed.candidateAt(CombinationCount.of(it.toLong())) })
    }

    @Test
    fun candidatesInRange_covers_half_open_interval_without_prior_scan() {
        val indexed = OdometerIndexedCandidateSpace(Alphabet.DIGITS, 2)
        val slice =
            indexed.candidatesInRange(CombinationCount.of(10), CombinationCount.of(15)).toList()
        assertEquals(listOf("10", "11", "12", "13", "14"), slice)
    }

    @Test
    fun candidateAt_rejects_out_of_bounds() {
        val indexed = OdometerIndexedCandidateSpace(Alphabet.of("ab"), 1)
        assertFailsWith<IllegalArgumentException> {
            indexed.candidateAt(CombinationCount.of(2))
        }
    }

    @Test
    fun supports_space_larger_than_64_bits_via_combination_count() {
        val indexed = OdometerIndexedCandidateSpace(Alphabet.ALPHANUMERIC, length = 12)
        assertTrue(indexed.size.fitsInLong().not())
        assertEquals(12, indexed.candidateAt(CombinationCount.ZERO).length)
        assertEquals(
            indexed.candidateAt(CombinationCount.of(3)),
            indexed.candidatesInRange(CombinationCount.of(3), CombinationCount.of(4)).single(),
        )
    }
}

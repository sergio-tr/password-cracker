package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LengthPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WepHexProgressiveAuditPolicyTest {
    @Test
    fun stages_cover40And104BitHexCasings() {
        val stages = WepHexProgressiveAuditPolicy.STAGES
        assertEquals(4, stages.size)
        assertEquals(100, stages.sumOf { it.budgetWeight })
        assertEquals(Alphabet.HEX_UPPER, stages[0].alphabet)
        assertEquals(LengthPolicy.exactly(10), stages[0].lengthPolicy)
        assertEquals(Alphabet.HEX_LOWER, stages[1].alphabet)
        assertEquals(LengthPolicy.exactly(10), stages[1].lengthPolicy)
        assertEquals(Alphabet.HEX_UPPER, stages[2].alphabet)
        assertEquals(LengthPolicy.exactly(26), stages[2].lengthPolicy)
        assertEquals(Alphabet.HEX_LOWER, stages[3].alphabet)
        assertEquals(LengthPolicy.exactly(26), stages[3].lengthPolicy)
    }

    @Test
    fun isValidHexKey_accepts10And26() {
        assertTrue(WepHexProgressiveAuditPolicy.isValidHexKey("0123456789"))
        assertTrue(WepHexProgressiveAuditPolicy.isValidHexKey("ABCDEF0123"))
        assertTrue(WepHexProgressiveAuditPolicy.isValidHexKey("abcdef0123"))
        assertTrue(WepHexProgressiveAuditPolicy.isValidHexKey("0123456789ABCDEF0123456789"))
        assertFalse(WepHexProgressiveAuditPolicy.isValidHexKey("12345678"))
        assertFalse(WepHexProgressiveAuditPolicy.isValidHexKey("GHIJKLMNOP"))
        assertFalse(WepHexProgressiveAuditPolicy.isValidHexKey(""))
    }
}

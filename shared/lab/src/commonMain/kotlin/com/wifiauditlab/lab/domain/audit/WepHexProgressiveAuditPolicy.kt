package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LengthPolicy

/**
 * Progressive stage policy for **local** WEP hex-key audits (never against the AP).
 *
 * IEEE 802.11 WEP hex entry is typically 10 hex digits (40-bit) or 26 hex digits
 * (104-bit). Stages try uppercase hex first (cheaper single casing), then lowercase.
 * Weights are relative integers (sum = [TOTAL_WEIGHT]) for budget allocation only.
 */
object WepHexProgressiveAuditPolicy {
    const val TOTAL_WEIGHT: Int = 100

    /** 40-bit WEP key as hex (5 bytes → 10 hex digits). */
    const val HEX_LEN_40: Int = 10

    /** 104-bit WEP key as hex (13 bytes → 26 hex digits). */
    const val HEX_LEN_104: Int = 26

    data class StageSpec(
        val id: String,
        val alphabet: Alphabet,
        val lengthPolicy: LengthPolicy,
        val priority: Int,
        val budgetWeight: Int,
        val mayOverlapPriorStages: Boolean,
    )

    val STAGES: List<StageSpec> =
        listOf(
            StageSpec(
                "wep-hex-upper-10",
                Alphabet.HEX_UPPER,
                LengthPolicy.exactly(HEX_LEN_40),
                priority = 1,
                budgetWeight = 40,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "wep-hex-lower-10",
                Alphabet.HEX_LOWER,
                LengthPolicy.exactly(HEX_LEN_40),
                priority = 2,
                budgetWeight = 20,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "wep-hex-upper-26",
                Alphabet.HEX_UPPER,
                LengthPolicy.exactly(HEX_LEN_104),
                priority = 3,
                budgetWeight = 30,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "wep-hex-lower-26",
                Alphabet.HEX_LOWER,
                LengthPolicy.exactly(HEX_LEN_104),
                priority = 4,
                budgetWeight = 10,
                mayOverlapPriorStages = false,
            ),
        )

    init {
        require(STAGES.sumOf { it.budgetWeight } == TOTAL_WEIGHT) {
            "stage weights must sum to $TOTAL_WEIGHT"
        }
        require(STAGES.map { it.priority } == STAGES.map { it.priority }.sorted()) {
            "stages must be listed in ascending priority"
        }
    }

    fun profileLabel(): String = "WEP hex"

    fun isValidHexKey(value: String): Boolean {
        if (value.length != HEX_LEN_40 && value.length != HEX_LEN_104) return false
        return value.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
    }

    fun exactSpace(
        alphabet: Alphabet,
        lengthPolicy: LengthPolicy,
    ): CombinationCount = GenericProgressiveAuditPolicy.exactSpace(alphabet, lengthPolicy)
}

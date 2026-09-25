package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LengthPolicy

/**
 * Progressive stage policy for Wi‑Fi personal PSK/SAE passphrase audits.
 *
 * All stages enforce WPA passphrase minimum length (≥ 8). Stages prefer cheaper
 * reduced alphabets first; the final stage may overlap earlier ones. Weights are
 * relative integers (sum = [TOTAL_WEIGHT]) for budget allocation only.
 *
 * [GenericProgressiveAuditPolicy] remains available for synthetic lab experiments
 * with short secrets; product audits must use this policy via the automatic planner.
 */
object WifiPskProgressiveAuditPolicy {
    const val TOTAL_WEIGHT: Int = 100

    /** Minimum passphrase length for Wi‑Fi PSK/SAE (IEEE 802.11). */
    const val MIN_PASSPHRASE_LENGTH: Int = 8

    /** Blind challenge upper bound — protocol max; search remains budget-capped. */
    const val MAX_PASSPHRASE_LENGTH: Int = 63

    data class StageSpec(
        val id: String,
        val alphabet: Alphabet,
        val lengthPolicy: LengthPolicy,
        val priority: Int,
        val budgetWeight: Int,
        val mayOverlapPriorStages: Boolean,
    )

    fun stagesFor(profile: SharedPasswordSearchProfile): List<StageSpec> {
        val prefix = profile.name.lowercase().replace('_', '-')
        val weights = weightsFor(profile)
        return listOf(
            StageSpec(
                "$prefix-digits-8",
                Alphabet.DIGITS,
                LengthPolicy.exactly(MIN_PASSPHRASE_LENGTH),
                priority = 1,
                budgetWeight = weights.digits8,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "$prefix-lower-8",
                Alphabet.LOWERCASE,
                LengthPolicy.exactly(MIN_PASSPHRASE_LENGTH),
                priority = 2,
                budgetWeight = weights.lower8,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "$prefix-lower-alnum-8",
                Alphabet.LOWER_ALPHANUMERIC,
                LengthPolicy.exactly(MIN_PASSPHRASE_LENGTH),
                priority = 3,
                budgetWeight = weights.lowerAlnum8,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "$prefix-lower-alnum-8-10",
                Alphabet.LOWER_ALPHANUMERIC,
                LengthPolicy(MIN_PASSPHRASE_LENGTH, 10),
                priority = 4,
                budgetWeight = weights.lowerAlnum8To10,
                mayOverlapPriorStages = false,
            ),
            StageSpec(
                "$prefix-alnum-8-12",
                Alphabet.ALPHANUMERIC,
                LengthPolicy(MIN_PASSPHRASE_LENGTH, 12),
                priority = 5,
                budgetWeight = weights.alnum8To12,
                mayOverlapPriorStages = true,
            ),
            StageSpec(
                "$prefix-printable-8-12",
                Alphabet.PRINTABLE_ASCII,
                LengthPolicy(MIN_PASSPHRASE_LENGTH, 12),
                priority = 6,
                budgetWeight = weights.printable8To12,
                mayOverlapPriorStages = true,
            ),
        ).also { stages ->
            require(stages.sumOf { it.budgetWeight } == TOTAL_WEIGHT) {
                "stage weights for $profile must sum to $TOTAL_WEIGHT"
            }
            require(stages.all { it.lengthPolicy.minLength >= MIN_PASSPHRASE_LENGTH }) {
                "PSK stages must not search below $MIN_PASSPHRASE_LENGTH characters"
            }
        }
    }

    fun exactSpace(
        alphabet: Alphabet,
        lengthPolicy: LengthPolicy,
    ): CombinationCount = GenericProgressiveAuditPolicy.exactSpace(alphabet, lengthPolicy)

    fun profileLabel(profile: SharedPasswordSearchProfile): String =
        when (profile) {
            SharedPasswordSearchProfile.WPA2_PERSONAL_PSK -> "WPA2-Personal PSK"
            SharedPasswordSearchProfile.WPA3_PERSONAL_PSK -> "WPA3-Personal SAE"
            SharedPasswordSearchProfile.WPA2_WPA3_TRANSITION_PSK -> "WPA2/WPA3 transition PSK"
            SharedPasswordSearchProfile.WPA_PERSONAL_PSK -> "WPA-Personal PSK"
        }

    private data class ProfileWeights(
        val digits8: Int,
        val lower8: Int,
        val lowerAlnum8: Int,
        val lowerAlnum8To10: Int,
        val alnum8To12: Int,
        val printable8To12: Int,
    )

    private fun weightsFor(profile: SharedPasswordSearchProfile): ProfileWeights =
        when (profile) {
            SharedPasswordSearchProfile.WPA3_PERSONAL_PSK ->
                ProfileWeights(
                    digits8 = 15,
                    lower8 = 15,
                    lowerAlnum8 = 15,
                    lowerAlnum8To10 = 15,
                    alnum8To12 = 25,
                    printable8To12 = 15,
                )
            SharedPasswordSearchProfile.WPA2_WPA3_TRANSITION_PSK ->
                ProfileWeights(
                    digits8 = 18,
                    lower8 = 18,
                    lowerAlnum8 = 18,
                    lowerAlnum8To10 = 18,
                    alnum8To12 = 20,
                    printable8To12 = 8,
                )
            SharedPasswordSearchProfile.WPA_PERSONAL_PSK,
            SharedPasswordSearchProfile.WPA2_PERSONAL_PSK,
            ->
                ProfileWeights(
                    digits8 = 20,
                    lower8 = 20,
                    lowerAlnum8 = 20,
                    lowerAlnum8To10 = 20,
                    alnum8To12 = 15,
                    printable8To12 = 5,
                )
        }
}

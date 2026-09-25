package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.lab.domain.Alphabet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WifiPskProgressiveAuditPolicyTest {
    @Test
    fun all_profiles_sum_weights_to_100() {
        SharedPasswordSearchProfile.entries.forEach { profile ->
            val stages = WifiPskProgressiveAuditPolicy.stagesFor(profile)
            assertEquals(100, stages.sumOf { it.budgetWeight })
            assertTrue(stages.all { it.lengthPolicy.minLength >= WifiPskProgressiveAuditPolicy.MIN_PASSPHRASE_LENGTH })
        }
    }

    @Test
    fun stage_alphabets_are_printable_ascii_subsets() {
        val printable = Alphabet.PRINTABLE_ASCII.symbols.toSet()
        SharedPasswordSearchProfile.entries.forEach { profile ->
            WifiPskProgressiveAuditPolicy.stagesFor(profile).forEach { stage ->
                assertTrue(stage.alphabet.symbols.all { it in printable })
            }
        }
    }

    @Test
    fun wpa3_profile_tilts_toward_longer_alnum_stage() {
        val wpa2 =
            WifiPskProgressiveAuditPolicy.stagesFor(SharedPasswordSearchProfile.WPA2_PERSONAL_PSK)
                .single { it.id.contains("alnum-8-12") }
        val wpa3 =
            WifiPskProgressiveAuditPolicy.stagesFor(SharedPasswordSearchProfile.WPA3_PERSONAL_PSK)
                .single { it.id.contains("alnum-8-12") }
        assertTrue(wpa3.budgetWeight > wpa2.budgetWeight)
    }
}

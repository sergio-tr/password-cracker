package com.wifiauditlab.lab.domain

import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Architectural guard: known-password audits must not let planners observe the target.
 */
class TargetIsolationTest {
    private val optimizer: SearchPlanOptimizer = DefaultSearchPlanOptimizer()

    @Test
    fun encapsulatedVerifier_neverAppearsInToString() {
        val secret = "SuperSecret-WiFi-99"
        val verifier = EncapsulatedPasswordVerifier.encapsulate(secret)
        assertFalse(verifier.toString().contains(secret))
        assertTrue(verifier.verify(secret))
        assertFalse(verifier.verify("other"))
    }

    @Test
    fun auditChallenge_policyIsIndependentOfTargetLength() {
        val target = "hunter2hunter2" // length 14
        val verifier = EncapsulatedPasswordVerifier.encapsulate(target)
        val blindPolicy = DefaultAutomaticPasswordAuditPlanner.BLIND_CHALLENGE_POLICY
        val challenge =
            LabChallenge.withEncapsulatedVerifier(
                policy = blindPolicy,
                verifier = verifier,
                seed = 7L,
            )

        assertNull(challenge.secretLength)
        assertEquals(8, challenge.lengthPolicy.minLength)
        assertEquals(63, challenge.lengthPolicy.maxLength)
        assertFalse(challenge.toString().contains(target))
        assertTrue(challenge.isSolution(target))
        assertFalse(challenge.isSolution("00000000"))

        val plan = optimizer.optimize(challenge, LengthPrioritizedStrategy.ID)
        // Plan space follows blind PSK policy — not length-14 alphanumeric of the target.
        assertEquals(blindPolicy.searchSpace, plan.searchSpace)
        assertFalse(plan.searchSpace.isZero)
    }

    @Test
    fun changingTarget_doesNotChangeBlindPlanSpace() {
        val policy = LabSecretPolicy(Alphabet.DIGITS, LengthPolicy(2, 4))
        val planA =
            optimizer.optimize(
                LabChallenge.withEncapsulatedVerifier(
                    policy,
                    EncapsulatedPasswordVerifier.encapsulate("aa"),
                    seed = 1L,
                ),
                LengthPrioritizedStrategy.ID,
            )
        val planB =
            optimizer.optimize(
                LabChallenge.withEncapsulatedVerifier(
                    policy,
                    EncapsulatedPasswordVerifier.encapsulate("zzzzzzzzzzzz"),
                    seed = 1L,
                ),
                LengthPrioritizedStrategy.ID,
            )
        assertEquals(planA.searchSpace, planB.searchSpace)
        assertEquals(planA.totalBuckets, planB.totalBuckets)
    }
}

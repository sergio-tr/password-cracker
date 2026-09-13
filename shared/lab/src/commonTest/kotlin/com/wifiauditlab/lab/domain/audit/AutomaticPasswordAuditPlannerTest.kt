package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import com.wifiauditlab.lab.engine.LabParallelEngineVersion
import com.wifiauditlab.lab.engine.NeverCancel
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import com.wifiauditlab.lab.engine.WorkerPoolConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AutomaticPasswordAuditPlannerTest {
    private val planner = DefaultAutomaticPasswordAuditPlanner()

    private val eligibleContext =
        PasswordAuditContext(sharedPasswordApplicable = true)

    private val basePerformance =
        PasswordAuditPerformanceProfile(
            calibratedAttemptsPerSecond = 20_000.0,
            availableProcessors = 4,
        )

    @Test
    fun identical_inputs_produce_identical_plans() {
        val budget = PasswordAuditBudget.standard()
        val a = ready(planner.createPlan(eligibleContext, basePerformance, budget))
        val b = ready(planner.createPlan(eligibleContext, basePerformance, budget))
        assertPlansEqual(a, b)
    }

    @Test
    fun planner_is_blind_to_target_passwords() {
        // Two completely different targets exist only for encapsulation elsewhere.
        // The planner never receives them; plans must match bit-for-bit on public fields.
        val targetA = EncapsulatedPasswordVerifier.encapsulate("aaaaaaaa")
        val targetB = EncapsulatedPasswordVerifier.encapsulate("9Z!q")
        assertNotNull(targetA)
        assertNotNull(targetB)

        val budget = PasswordAuditBudget.standard()
        val planA = ready(planner.createPlan(eligibleContext, basePerformance, budget))
        val planB = ready(planner.createPlan(eligibleContext, basePerformance, budget))
        assertPlansEqual(planA, planB)
        assertFalse(planA.toString().contains("aaaaaaaa", ignoreCase = false))
        assertFalse(planB.toString().contains("9Z!q", ignoreCase = false))
    }

    @Test
    fun different_throughput_may_change_workers_or_capacity_not_policy() {
        val low =
            ready(
                planner.createPlan(
                    eligibleContext,
                    PasswordAuditPerformanceProfile(1_000.0, availableProcessors = 8),
                    PasswordAuditBudget.standard(),
                ),
            )
        val high =
            ready(
                planner.createPlan(
                    eligibleContext,
                    PasswordAuditPerformanceProfile(200_000.0, availableProcessors = 8),
                    PasswordAuditBudget.standard(),
                ),
            )
        assertEquals(low.stages.map { it.id }, high.stages.map { it.id })
        assertEquals(low.searchPlan.strategyId, high.searchPlan.strategyId)
        assertTrue(low.workerCount <= high.workerCount)
        assertEquals(1, low.workerCount)
        assertTrue(high.workerCount >= 2)
    }

    @Test
    fun different_budgets_change_capacity() {
        val quick = ready(planner.createPlan(eligibleContext, basePerformance, PasswordAuditBudget.quick()))
        val standard = ready(planner.createPlan(eligibleContext, basePerformance, PasswordAuditBudget.standard()))
        assertNotNull(quick.budgetedAttemptCapacity)
        assertNotNull(standard.budgetedAttemptCapacity)
        assertTrue(quick.budgetedAttemptCapacity!! < standard.budgetedAttemptCapacity!!)
        assertTrue(quick.searchLimits.maxDuration!! < standard.searchLimits.maxDuration!!)
    }

    @Test
    fun huge_spaces_use_combination_count_without_overflow() {
        val plan = ready(planner.createPlan(eligibleContext, basePerformance, PasswordAuditBudget.deep()))
        assertFalse(plan.totalCandidateSpace.isZero)
        assertTrue(plan.totalCandidateSpace.toExactString().isNotBlank())
        val stageSum =
            plan.stages.fold(CombinationCount.ZERO) { acc, stage -> acc + stage.estimatedSpace }
        assertEquals(stageSum, plan.totalCandidateSpace)
        // Prove exact arithmetic beyond Long without materializing candidates.
        val beyondLong = CombinationCount.alphabetPower(36, 40)
        assertEquals(null, beyondLong.toLongOrNull())
        assertTrue(beyondLong > CombinationCount.of(Long.MAX_VALUE))
    }

    @Test
    fun unsupported_authentication_yields_not_applicable() {
        val result =
            planner.createPlan(
                PasswordAuditContext(
                    sharedPasswordApplicable = false,
                    inapplicableReason = "OPEN networks have no shared password",
                ),
                basePerformance,
                PasswordAuditBudget.standard(),
            )
        val denied = assertIs<PasswordAuditPlanResult.NotApplicable>(result)
        assertTrue(denied.reason.contains("OPEN") || denied.reason.contains("not applicable"))
    }

    @Test
    fun workers_never_zero_negative_or_above_safe_max() {
        val plan =
            ready(
                planner.createPlan(
                    eligibleContext,
                    PasswordAuditPerformanceProfile(1_000_000.0, availableProcessors = 64),
                    PasswordAuditBudget.standard(),
                ),
            )
        assertTrue(plan.workerCount in 1..WorkerPoolConfig.DEFAULT_MAX_WORKERS)
    }

    @Test
    fun stage_invariants_hold() {
        val plan = ready(planner.createPlan(eligibleContext, basePerformance, PasswordAuditBudget.standard()))
        assertEquals(GenericProgressiveAuditPolicy.STAGES.size, plan.stages.size)
        assertEquals(plan.stages.map { it.priority }, plan.stages.map { it.priority }.sorted())
        assertTrue(plan.stages.none { it.estimatedSpace.isZero })

        val globalAttempts = plan.budgetedAttemptCapacity
        if (globalAttempts != null) {
            val stageSum =
                plan.stages.mapNotNull { it.budget.maxAttempts }
                    .fold(CombinationCount.ZERO) { acc, c -> acc + c }
            assertEquals(globalAttempts, stageSum)
        }
        plan.stages.forEach { stage ->
            val expected =
                GenericProgressiveAuditPolicy.exactSpace(
                    stage.candidateModel.alphabet,
                    stage.candidateModel.lengthPolicy,
                )
            assertEquals(expected, stage.estimatedSpace)
        }
        assertTrue(plan.stagesMayOverlap)
        assertTrue(
            plan.feasibility.rating in
                setOf(
                    FeasibilityRating.Reasonable,
                    FeasibilityRating.Expensive,
                    FeasibilityRating.Impractical,
                    FeasibilityRating.Invalid,
                ),
        )
    }

    @Test
    fun engine_choice_follows_worker_count() {
        val single =
            ready(
                planner.createPlan(
                    eligibleContext,
                    PasswordAuditPerformanceProfile(1_000.0, 4),
                    PasswordAuditBudget.standard(),
                ),
            )
        assertIs<PasswordAuditEngineChoice.BaselineSequential>(single.engine)

        val multi =
            ready(
                planner.createPlan(
                    eligibleContext,
                    PasswordAuditPerformanceProfile(200_000.0, 4),
                    PasswordAuditBudget.standard(),
                ),
            )
        val parallel = assertIs<PasswordAuditEngineChoice.Parallel>(multi.engine)
        assertEquals(LabParallelEngineVersion.V2, parallel.version)
    }

    @Test
    fun explanation_is_novice_friendly() {
        val plan = ready(planner.createPlan(eligibleContext, basePerformance, PasswordAuditBudget.standard()))
        assertEquals("Modo automático", plan.explanation.headline)
        assertTrue(plan.explanation.details.any { it.contains("workers") })
        assertTrue(plan.explanation.details.any { it.contains("etapas") })
        assertFalse(plan.explanation.details.any { it.contains("DynamicRange") })
        assertFalse(plan.explanation.details.any { it.contains("SearchBucket") })
    }

    @Test
    fun budget_rejects_unbounded() {
        try {
            PasswordAuditBudget(maxDuration = null, maxAttempts = null)
            kotlin.test.fail("expected require failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun integration_planner_to_engine_with_encapsulated_verifier() =
        runTest {
            val plan =
                ready(
                    planner.createPlan(
                        eligibleContext,
                        PasswordAuditPerformanceProfile(50_000.0, 4),
                        PasswordAuditBudget(
                            maxDuration = 30.seconds,
                            maxAttempts = CombinationCount.of(50_000),
                        ),
                    ),
                )
            val verifier = EncapsulatedPasswordVerifier.encapsulate("42")
            val challenge =
                LabChallenge.withEncapsulatedVerifier(
                    policy = plan.blindChallengePolicy,
                    verifier = verifier,
                    seed = plan.searchPlan.seed,
                )
            val engine = WorkerAwareLabSearchEngine(availableProcessors = 4)
            engine.workers = plan.workerCount
            if (plan.engine is PasswordAuditEngineChoice.Parallel) {
                engine.parallelVersion =
                    (plan.engine as PasswordAuditEngineChoice.Parallel).version
            }
            val events =
                engine.run(challenge, plan.searchPlan, plan.searchLimits, NeverCancel).toList()
            val terminal = events.last()
            assertTrue(
                terminal is LabSearchEvent.CandidateFound || terminal is LabSearchEvent.LimitReached,
                "expected found or limit, was $terminal",
            )
            if (terminal is LabSearchEvent.CandidateFound) {
                assertEquals("42", terminal.candidate)
            }
        }

    private fun ready(result: PasswordAuditPlanResult): PasswordAuditPlan =
        assertIs<PasswordAuditPlanResult.Ready>(result).plan

    private fun assertPlansEqual(
        a: PasswordAuditPlan,
        b: PasswordAuditPlan,
    ) {
        assertEquals(a.searchPlan.strategyId, b.searchPlan.strategyId)
        assertEquals(a.searchPlan.seed, b.searchPlan.seed)
        assertEquals(a.searchPlan.buckets.size, b.searchPlan.buckets.size)
        a.searchPlan.buckets.zip(b.searchPlan.buckets).forEach { (left, right) ->
            assertEquals(left.index, right.index)
            assertEquals(left.length, right.length)
            assertEquals(left.alphabet, right.alphabet)
            assertEquals(left.searchSpaceSize, right.searchSpaceSize)
        }
        assertEquals(a.stages.map { it.id to it.estimatedSpace }, b.stages.map { it.id to it.estimatedSpace })
        assertEquals(a.workerCount, b.workerCount)
        assertEquals(a.engine, b.engine)
        assertEquals(a.totalCandidateSpace, b.totalCandidateSpace)
        assertEquals(a.budgetedAttemptCapacity, b.budgetedAttemptCapacity)
        assertEquals(a.searchLimits, b.searchLimits)
        assertEquals(a.explanation, b.explanation)
    }
}

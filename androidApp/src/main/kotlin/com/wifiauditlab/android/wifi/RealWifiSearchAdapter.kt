package com.wifiauditlab.android.wifi

import com.wifiauditlab.android.ui.audit.toSharedPasswordSearchProfile
import com.wifiauditlab.assessment.domain.realaudit.ExternalCandidateVerificationPort
import com.wifiauditlab.assessment.domain.realaudit.ExternalVerificationResult
import com.wifiauditlab.assessment.domain.realaudit.ExternalVerifierCapability
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditFailureReason
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgress
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditUnavailableReason
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchExecutionResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchPort
import com.wifiauditlab.assessment.domain.realaudit.requiresPasswordSearch
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudget
import com.wifiauditlab.lab.domain.audit.PasswordAuditContext
import com.wifiauditlab.lab.domain.audit.PasswordAuditEngineChoice
import com.wifiauditlab.lab.domain.audit.PasswordAuditPerformanceProfile
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlanResult
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.CandidateVerifier
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.engine.LabParallelEngineVersion
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reuses Lab Search Engine V2 for RealWifiAudit with an **opaque** external verifier.
 *
 * Does **not** use EncapsulatedPasswordVerifier or any known-secret.
 * If [externalVerifier] capability is Unavailable, returns immediately —
 * never starts a search against a local password.
 */
class RealWifiSearchAdapter(
    private val engine: LabSearchEngine,
    private val externalVerifier: ExternalCandidateVerificationPort,
    private val planner: AutomaticPasswordAuditPlanner = DefaultAutomaticPasswordAuditPlanner(),
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    private val calibratedAttemptsPerSecond: Double? = null,
) : RealWifiSearchPort {
    @Volatile
    private var cancellation: CancellationController? = null

    override fun cancel() {
        cancellation?.cancel()
        externalVerifier.cancel()
    }

    override suspend fun search(
        plan: RealWifiAuditPlan,
        progress: RealWifiAuditProgressSink,
    ): RealWifiSearchExecutionResult {
        if (!plan.strategy.requiresPasswordSearch()) {
            return RealWifiSearchExecutionResult.Unavailable(
                RealWifiAuditUnavailableReason.NotImplemented,
            )
        }
        if (externalVerifier.capability() is ExternalVerifierCapability.Unavailable) {
            return RealWifiSearchExecutionResult.Unavailable(
                RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
            )
        }

        val searchProfile =
            plan.securityProfile.family.toSharedPasswordSearchProfile()
                ?: return RealWifiSearchExecutionResult.Unavailable(
                    RealWifiAuditUnavailableReason.CapabilityUnavailable,
                )

        val budget =
            PasswordAuditBudget(
                maxDuration = plan.limits.maxDurationMillis.milliseconds,
            )
        val context =
            PasswordAuditContext(
                sharedPasswordApplicable = true,
                searchProfile = searchProfile,
            )
        val performance =
            PasswordAuditPerformanceProfile(
                calibratedAttemptsPerSecond = calibratedAttemptsPerSecond,
                availableProcessors = availableProcessors,
            )

        val auditPlan =
            when (val result = planner.createPlan(context, performance, budget)) {
                is PasswordAuditPlanResult.Ready -> result.plan
                is PasswordAuditPlanResult.NotApplicable ->
                    return RealWifiSearchExecutionResult.Unavailable(
                        RealWifiAuditUnavailableReason.CapabilityUnavailable,
                    )
            }

        val controller = CancellationController()
        cancellation = controller

        val aware = engine as? WorkerAwareLabSearchEngine
        aware?.workers = auditPlan.workerCount
        when (val choice = auditPlan.engine) {
            is PasswordAuditEngineChoice.Parallel ->
                aware?.parallelVersion = choice.version
            PasswordAuditEngineChoice.BaselineSequential ->
                aware?.parallelVersion = LabParallelEngineVersion.V2
        }

        // Opaque CandidateVerifier: SE never sees the secret — only Match/NoMatch.
        val verifier =
            CandidateVerifier { candidate ->
                when (
                    runBlocking {
                        externalVerifier.verify(candidate)
                    }
                ) {
                    ExternalVerificationResult.Match -> true
                    ExternalVerificationResult.NoMatch -> false
                    ExternalVerificationResult.Cancelled -> false
                    ExternalVerificationResult.Unavailable,
                    is ExternalVerificationResult.Failed,
                    -> false
                }
            }

        val challenge =
            LabChallenge.withEncapsulatedVerifier(
                policy = auditPlan.blindChallengePolicy,
                verifier = verifier,
                seed = auditPlan.searchPlan.seed,
            )

        fun emit(metrics: SearchMetrics?) {
            val m = metrics
            progress.onProgress(
                RealWifiAuditProgress(
                    strategyLabel = plan.strategyLabel,
                    candidatesProcessed = m?.attempts?.toLongOrNull() ?: 0L,
                    elapsedMillis = m?.elapsed?.inWholeMilliseconds ?: 0L,
                    ratePerSecond = m?.attemptsPerSecond?.takeIf { it > 0.0 },
                    workerCount = auditPlan.workerCount,
                    statusMessage = "searching",
                ),
            )
        }

        progress.onProgress(
            RealWifiAuditProgress(
                strategyLabel = plan.strategyLabel,
                workerCount = auditPlan.workerCount,
                statusMessage = "searching",
            ),
        )

        return try {
            var terminal: LabSearchEvent? = null
            engine
                .run(challenge, auditPlan.searchPlan, auditPlan.searchLimits, controller)
                .collect { event ->
                    when (event) {
                        LabSearchEvent.Preparing,
                        is LabSearchEvent.Started,
                        -> Unit
                        is LabSearchEvent.Progress -> emit(event.metrics)
                        is LabSearchEvent.CandidateFound,
                        is LabSearchEvent.Completed,
                        is LabSearchEvent.LimitReached,
                        is LabSearchEvent.Cancelled,
                        is LabSearchEvent.Failed,
                        -> terminal = event
                    }
                }

            when (val end = terminal) {
                null ->
                    RealWifiSearchExecutionResult.Cancelled(
                        RealWifiAuditProgress(
                            strategyLabel = plan.strategyLabel,
                            workerCount = auditPlan.workerCount,
                            statusMessage = "cancelled",
                        ),
                    )
                is LabSearchEvent.CandidateFound -> {
                    emit(end.metrics)
                    RealWifiSearchExecutionResult.Found(
                        progress =
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                candidatesProcessed = end.metrics.attempts.toLongOrNull() ?: 0L,
                                elapsedMillis = end.metrics.elapsed.inWholeMilliseconds,
                                ratePerSecond = end.metrics.attemptsPerSecond.takeIf { it > 0.0 },
                                workerCount = auditPlan.workerCount,
                                statusMessage = "found",
                            ),
                    )
                }
                is LabSearchEvent.Completed -> {
                    emit(end.metrics)
                    RealWifiSearchExecutionResult.Exhausted(
                        RealWifiAuditProgress(
                            strategyLabel = plan.strategyLabel,
                            candidatesProcessed = end.metrics.attempts.toLongOrNull() ?: 0L,
                            elapsedMillis = end.metrics.elapsed.inWholeMilliseconds,
                            ratePerSecond = end.metrics.attemptsPerSecond.takeIf { it > 0.0 },
                            workerCount = auditPlan.workerCount,
                            statusMessage = "exhausted",
                        ),
                    )
                }
                is LabSearchEvent.LimitReached -> {
                    emit(end.metrics)
                    RealWifiSearchExecutionResult.LimitReached(
                        RealWifiAuditProgress(
                            strategyLabel = plan.strategyLabel,
                            candidatesProcessed = end.metrics.attempts.toLongOrNull() ?: 0L,
                            elapsedMillis = end.metrics.elapsed.inWholeMilliseconds,
                            ratePerSecond = end.metrics.attemptsPerSecond.takeIf { it > 0.0 },
                            workerCount = auditPlan.workerCount,
                            statusMessage = "limit_reached",
                        ),
                    )
                }
                is LabSearchEvent.Cancelled -> {
                    emit(end.metrics)
                    RealWifiSearchExecutionResult.Cancelled(
                        RealWifiAuditProgress(
                            strategyLabel = plan.strategyLabel,
                            candidatesProcessed = end.metrics.attempts.toLongOrNull() ?: 0L,
                            elapsedMillis = end.metrics.elapsed.inWholeMilliseconds,
                            ratePerSecond = end.metrics.attemptsPerSecond.takeIf { it > 0.0 },
                            workerCount = auditPlan.workerCount,
                            statusMessage = "cancelled",
                        ),
                    )
                }
                is LabSearchEvent.Failed ->
                    RealWifiSearchExecutionResult.Failed(
                        RealWifiAuditFailureReason.SearchFailed,
                        end.metrics?.let {
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                candidatesProcessed = it.attempts.toLongOrNull() ?: 0L,
                                elapsedMillis = it.elapsed.inWholeMilliseconds,
                                ratePerSecond = it.attemptsPerSecond.takeIf { r -> r > 0.0 },
                                workerCount = auditPlan.workerCount,
                                statusMessage = "failed",
                            )
                        },
                    )
                else ->
                    RealWifiSearchExecutionResult.Failed(
                        RealWifiAuditFailureReason.SearchFailed,
                        null,
                    )
            }
        } finally {
            cancellation = null
        }
    }
}

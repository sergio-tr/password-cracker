package com.wifiauditlab.android.ui.realaudit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditTechnicalDetails
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditUnavailableReason
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchOutcome
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealWifiAuditScreen(
    onBack: () -> Unit,
    viewModel: RealWifiAuditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigateBack = stringResource(R.string.navigate_back)
    val startCd = stringResource(R.string.real_audit_start)
    val stopCd = stringResource(R.string.real_audit_stop)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.real_audit_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = navigateBack },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            if (state.isBusy) {
                OutlinedButton(
                    onClick = viewModel::stop,
                    enabled = state.phase !is RealWifiAuditPhase.Stopping,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .semantics { contentDescription = stopCd },
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        if (state.phase is RealWifiAuditPhase.Stopping) {
                            stringResource(R.string.real_audit_stopping)
                        } else {
                            stringResource(R.string.real_audit_stop)
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.missingTarget) {
                Text(stringResource(R.string.real_audit_missing_target))
                return@Column
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.real_audit_auditing, state.displayName),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.real_audit_security_heading), fontWeight = FontWeight.SemiBold)
                    Text(
                        state.securityFamily?.let { stringResource(familyLabelRes(it)) }
                            ?: stringResource(R.string.real_audit_security_unknown),
                    )
                    Text(stringResource(R.string.real_audit_strategy_heading), fontWeight = FontWeight.SemiBold)
                    Text(state.strategyLabel)
                    Text(stringResource(R.string.real_audit_phase_heading), fontWeight = FontWeight.SemiBold)
                    Text(phaseLabel(state.phase))
                }
            }

            if (state.showBackendUnavailableCard) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .testTag("real_audit_backend_unavailable"),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.real_audit_outcome_external_verifier),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(R.string.real_audit_external_verifier_detail))
                    }
                }
            }

            if (state.isBusy ||
                state.phase is RealWifiAuditPhase.Completed ||
                state.phase is RealWifiAuditPhase.Failed
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.real_audit_metrics_heading), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.real_audit_candidates,
                                state.candidatesProcessed,
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.real_audit_elapsed_fmt,
                                formatElapsed(state.elapsedMillis),
                            ),
                        )
                        state.ratePerSecond?.let {
                            Text(stringResource(R.string.real_audit_rate, it))
                        }
                        Text(stringResource(R.string.real_audit_workers, state.workerCount))
                    }
                }
            }

            when (val phase = state.phase) {
                is RealWifiAuditPhase.Completed ->
                    OutcomeCard(
                        outcome = phase.outcome,
                        search = state.searchOutcome,
                    )
                is RealWifiAuditPhase.Failed ->
                    Text(stringResource(R.string.real_audit_outcome_failed))
                else -> Unit
            }

            state.technicalDetails?.let { details ->
                TechnicalDetailsCard(details)
            }

            if (!state.missingTarget &&
                (
                    state.canStart ||
                        state.showBackendUnavailableCard ||
                        state.phase is RealWifiAuditPhase.Ready
                )
            ) {
                Button(
                    onClick = viewModel::start,
                    enabled = state.canStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = startCd },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.real_audit_start))
                }
                if (state.showBackendUnavailableCard) {
                    Text(stringResource(R.string.real_audit_start_blocked_backend))
                }
            }
        }
    }
}

@Composable
private fun TechnicalDetailsCard(details: RealWifiAuditTechnicalDetails) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.real_audit_technical_details),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.real_audit_tech_strategy,
                    details.strategyName,
                ),
            )
            Text(
                stringResource(
                    R.string.real_audit_tech_planner,
                    if (details.plannerReady) {
                        stringResource(R.string.real_audit_tech_ready)
                    } else {
                        stringResource(R.string.real_audit_tech_not_ready)
                    },
                ),
            )
            Text(
                stringResource(
                    R.string.real_audit_tech_search_engine,
                    if (details.searchEngineReady) {
                        stringResource(R.string.real_audit_tech_ready)
                    } else {
                        stringResource(R.string.real_audit_tech_not_ready)
                    },
                ),
            )
            Text(
                stringResource(
                    R.string.real_audit_tech_external_verifier,
                    externalVerifierStatusLabel(details.externalVerifierLabel),
                ),
            )
        }
    }
}

@Composable
private fun OutcomeCard(
    outcome: RealWifiAuditOutcome,
    search: RealWifiSearchOutcome?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.real_audit_result_heading), fontWeight = FontWeight.SemiBold)
            Text(
                when (outcome) {
                    is RealWifiAuditOutcome.Completed ->
                        stringResource(R.string.real_audit_outcome_completed)
                    RealWifiAuditOutcome.Cancelled ->
                        stringResource(R.string.real_audit_outcome_cancelled)
                    RealWifiAuditOutcome.Unsupported ->
                        stringResource(R.string.real_audit_outcome_unsupported)
                    is RealWifiAuditOutcome.AssessmentOnly ->
                        stringResource(R.string.real_audit_outcome_assessment_only)
                    is RealWifiAuditOutcome.Unavailable ->
                        when (outcome.reason) {
                            RealWifiAuditUnavailableReason.NotImplemented ->
                                stringResource(R.string.real_audit_outcome_not_implemented)
                            RealWifiAuditUnavailableReason.MissingPermissions ->
                                stringResource(R.string.real_audit_outcome_permissions)
                            RealWifiAuditUnavailableReason.CapabilityUnavailable ->
                                stringResource(R.string.real_audit_outcome_capability)
                            RealWifiAuditUnavailableReason.ExternalVerifierUnavailable ->
                                stringResource(R.string.real_audit_outcome_external_verifier)
                        }
                },
            )
            search?.let {
                Text(stringResource(R.string.real_audit_search_result_heading), fontWeight = FontWeight.SemiBold)
                Text(searchOutcomeLabel(it))
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: RealWifiAuditPhase): String =
    when (phase) {
        RealWifiAuditPhase.Idle -> stringResource(R.string.real_audit_phase_idle)
        RealWifiAuditPhase.CheckingCapability ->
            stringResource(R.string.real_audit_phase_checking_capability)
        RealWifiAuditPhase.Preparing -> stringResource(R.string.real_audit_phase_preparing)
        RealWifiAuditPhase.Ready -> stringResource(R.string.real_audit_phase_ready)
        RealWifiAuditPhase.BackendUnavailable ->
            stringResource(R.string.real_audit_phase_backend_unavailable)
        RealWifiAuditPhase.Searching -> stringResource(R.string.real_audit_phase_searching)
        RealWifiAuditPhase.Stopping -> stringResource(R.string.real_audit_phase_stopping)
        is RealWifiAuditPhase.Completed -> stringResource(R.string.real_audit_phase_completed)
        is RealWifiAuditPhase.Failed -> stringResource(R.string.real_audit_phase_failed)
    }

@Composable
private fun searchOutcomeLabel(outcome: RealWifiSearchOutcome): String =
    when (outcome) {
        RealWifiSearchOutcome.Found -> stringResource(R.string.real_audit_search_found)
        RealWifiSearchOutcome.Exhausted -> stringResource(R.string.real_audit_search_exhausted)
        RealWifiSearchOutcome.LimitReached -> stringResource(R.string.real_audit_search_limit)
        RealWifiSearchOutcome.Cancelled -> stringResource(R.string.real_audit_search_cancelled)
    }

@Composable
private fun externalVerifierStatusLabel(raw: String): String =
    when (raw) {
        "Available" -> stringResource(R.string.real_audit_tech_available)
        "Unavailable" -> stringResource(R.string.real_audit_tech_unavailable)
        "NotRequired" -> stringResource(R.string.real_audit_tech_not_required)
        else -> raw
    }

private fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

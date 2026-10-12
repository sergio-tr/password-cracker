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
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditUnavailableReason
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
                    Text(state.displayName, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.real_audit_security_heading), fontWeight = FontWeight.SemiBold)
                    Text(
                        state.securityFamily?.let { stringResource(familyLabelRes(it)) }
                            ?: stringResource(R.string.real_audit_security_unknown),
                    )
                    Text(stringResource(R.string.real_audit_strategy_heading), fontWeight = FontWeight.SemiBold)
                    Text(state.strategyLabel)
                    Text(stringResource(R.string.real_audit_status_heading), fontWeight = FontWeight.SemiBold)
                    Text(phaseLabel(state.phase))
                }
            }

            if (state.isBusy || state.phase is RealWifiAuditPhase.Completed || state.phase is RealWifiAuditPhase.Failed) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.real_audit_metrics_heading), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.real_audit_candidates,
                                state.candidatesProcessed,
                            ),
                        )
                        Text(stringResource(R.string.real_audit_elapsed, state.elapsedMillis))
                        state.ratePerSecond?.let {
                            Text(stringResource(R.string.real_audit_rate, it))
                        }
                        Text(stringResource(R.string.real_audit_workers, state.workerCount))
                    }
                }
            }

            when (val phase = state.phase) {
                is RealWifiAuditPhase.Completed -> OutcomeCard(phase.outcome)
                is RealWifiAuditPhase.Failed ->
                    Text(stringResource(R.string.real_audit_outcome_failed))
                else -> Unit
            }

            if (state.canStart) {
                Button(
                    onClick = viewModel::start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = startCd },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.real_audit_start))
                }
            }
        }
    }
}

@Composable
private fun OutcomeCard(outcome: RealWifiAuditOutcome) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.real_audit_result_heading), fontWeight = FontWeight.SemiBold)
            Text(
                when (outcome) {
                    RealWifiAuditOutcome.Completed -> stringResource(R.string.real_audit_outcome_completed)
                    RealWifiAuditOutcome.Cancelled -> stringResource(R.string.real_audit_outcome_cancelled)
                    RealWifiAuditOutcome.Unsupported -> stringResource(R.string.real_audit_outcome_unsupported)
                    is RealWifiAuditOutcome.Unavailable ->
                        when (outcome.reason) {
                            RealWifiAuditUnavailableReason.NotImplemented ->
                                stringResource(R.string.real_audit_outcome_not_implemented)
                            RealWifiAuditUnavailableReason.MissingPermissions ->
                                stringResource(R.string.real_audit_outcome_permissions)
                            RealWifiAuditUnavailableReason.CapabilityUnavailable ->
                                stringResource(R.string.real_audit_outcome_capability)
                        }
                },
            )
        }
    }
}

@Composable
private fun phaseLabel(phase: RealWifiAuditPhase): String =
    when (phase) {
        RealWifiAuditPhase.Idle -> stringResource(R.string.real_audit_phase_idle)
        RealWifiAuditPhase.Preparing -> stringResource(R.string.real_audit_phase_preparing)
        RealWifiAuditPhase.Ready -> stringResource(R.string.real_audit_phase_ready)
        RealWifiAuditPhase.Running -> stringResource(R.string.real_audit_phase_running)
        RealWifiAuditPhase.Stopping -> stringResource(R.string.real_audit_phase_stopping)
        is RealWifiAuditPhase.Completed -> stringResource(R.string.real_audit_phase_completed)
        is RealWifiAuditPhase.Failed -> stringResource(R.string.real_audit_phase_failed)
    }

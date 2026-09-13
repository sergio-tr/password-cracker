package com.wifiauditlab.android.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.audit.EvidenceQuality
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PasswordAuditScreen(
    viewModel: PasswordAuditViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startLabel = stringResource(R.string.audit_start)
    val stopLabel = stringResource(R.string.audit_stop)
    val stoppingLabel = stringResource(R.string.audit_stopping)
    val backCd = stringResource(R.string.audit_navigate_back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isActive) {
                            stringResource(R.string.audit_running_title, state.displayName)
                        } else {
                            stringResource(R.string.audit_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backCd },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!state.missingTarget) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.infoMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.isActive) {
                            Button(
                                onClick = viewModel::stop,
                                enabled = state.searchState != SearchState.Cancelling,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("audit_stop")
                                        .semantics { contentDescription = stopLabel },
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    if (state.searchState == SearchState.Cancelling) {
                                        stoppingLabel
                                    } else {
                                        stopLabel
                                    },
                                )
                            }
                        } else {
                            Button(
                                onClick = viewModel::onStartAuditClicked,
                                enabled = state.startBlockedReason == null && state.plan != null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("audit_start")
                                        .semantics { contentDescription = startLabel },
                            ) {
                                Text(startLabel)
                            }
                            state.startBlockedReason?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
            when {
                state.missingTarget ->
                    Text(
                        stringResource(R.string.audit_missing_target),
                        modifier = Modifier.testTag("audit_missing_target"),
                    )
                else -> {
                    NetworkBanner(state)
                    state.connectionLostMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.isActive || state.outcome != null) {
                        ExecutionStatusCard(state)
                    }
                    if (state.outcome != null && !state.isActive) {
                        AuditResultReportCard(state, viewModel)
                    }
                    if (!state.isActive) {
                        PasswordSection(state, viewModel)
                        ModeSection(state, viewModel)
                        DurationSection(state, viewModel)
                        if (state.mode == PasswordAuditInteractionMode.Advanced ||
                            state.advancedExpanded ||
                            state.preset == PasswordAuditBudgetPreset.Custom
                        ) {
                            AdvancedBudgetSection(state, viewModel)
                        }
                    }
                    PlanSection(state, viewModel)
                    if (state.resultReport == null) {
                        state.strength?.let { StrengthHint(it.summary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkBanner(state: PasswordAuditUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(state.displayName, fontWeight = FontWeight.Bold)
            if (state.ssidLabel.isNotBlank() && state.ssidLabel != state.displayName) {
                Text(state.ssidLabel)
            }
            Text(state.familyLabel)
            Text(
                "● ${state.connectedLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.audit_available),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.audit_local_only_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PasswordSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.audit_password_section), fontWeight = FontWeight.SemiBold)
            if (state.vaultSecretAvailable) {
                SecretSourceRow(
                    selected = state.secretSource == PasswordAuditSecretSource.Vault,
                    label = stringResource(R.string.audit_use_vault_saved),
                    contentDescription = stringResource(R.string.audit_use_vault_saved),
                    onClick = { viewModel.selectSecretSource(PasswordAuditSecretSource.Vault) },
                )
                SecretSourceRow(
                    selected = state.secretSource == PasswordAuditSecretSource.Manual,
                    label = stringResource(R.string.audit_enter_other_password),
                    contentDescription = stringResource(R.string.audit_enter_other_password),
                    onClick = { viewModel.selectSecretSource(PasswordAuditSecretSource.Manual) },
                )
            }
            if (state.secretSource == PasswordAuditSecretSource.Vault) {
                Text(
                    stringResource(R.string.audit_vault_deferred),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                ManualPasswordFields(state, viewModel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Guardar en Vault"
                            },
                ) {
                    Checkbox(
                        checked = state.saveToVault,
                        onCheckedChange = viewModel::setSaveToVault,
                    )
                    Text(stringResource(R.string.audit_save_to_vault))
                }
            }
        }
    }
}

@Composable
private fun SecretSourceRow(
    selected: Boolean,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ManualPasswordFields(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    val passwordCd = stringResource(R.string.audit_password_label)
    OutlinedTextField(
        value = state.passwordInput,
        onValueChange = viewModel::onPasswordChanged,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = passwordCd },
        label = { Text(passwordCd) },
        singleLine = true,
        isError = state.passwordError != null,
        visualTransformation =
            if (state.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        trailingIcon = {
            IconButton(onClick = viewModel::togglePasswordVisibility) {
                Icon(
                    imageVector =
                        if (state.passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                    contentDescription =
                        if (state.passwordVisible) {
                            stringResource(R.string.audit_hide)
                        } else {
                            stringResource(R.string.audit_show)
                        },
                )
            }
        },
        supportingText = {
            state.passwordError?.let { Text(it) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audit_mode), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == PasswordAuditInteractionMode.Automatic,
                onClick = { viewModel.selectMode(PasswordAuditInteractionMode.Automatic) },
                label = { Text(stringResource(R.string.audit_mode_automatic)) },
                modifier = Modifier.semantics { contentDescription = "Modo Automático" },
            )
            FilterChip(
                selected = state.mode == PasswordAuditInteractionMode.Advanced,
                onClick = { viewModel.selectMode(PasswordAuditInteractionMode.Advanced) },
                label = { Text(stringResource(R.string.audit_mode_advanced)) },
                modifier = Modifier.semantics { contentDescription = "Modo Avanzado" },
            )
        }
        if (state.mode == PasswordAuditInteractionMode.Advanced) {
            OutlinedButton(
                onClick = viewModel::resetToAutomaticDefaults,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Restablecer configuración automática" },
            ) {
                Text(stringResource(R.string.audit_reset_automatic))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audit_budget), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PasswordAuditBudgetPreset.Quick,
                PasswordAuditBudgetPreset.Standard,
                PasswordAuditBudgetPreset.Deep,
                PasswordAuditBudgetPreset.Custom,
            ).forEach { preset ->
                val label =
                    when (preset) {
                        PasswordAuditBudgetPreset.Quick -> stringResource(R.string.audit_preset_quick)
                        PasswordAuditBudgetPreset.Standard -> stringResource(R.string.audit_preset_standard)
                        PasswordAuditBudgetPreset.Deep -> stringResource(R.string.audit_preset_deep)
                        PasswordAuditBudgetPreset.Custom -> stringResource(R.string.audit_preset_custom)
                    }
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { viewModel.selectPreset(preset) },
                    label = { Text(label) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Preset $label"
                        },
                )
            }
        }
        Text(
            stringResource(R.string.audit_estimation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvancedBudgetSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.audit_custom_limits), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.customDurationSeconds,
                onValueChange = viewModel::onCustomDurationChanged,
                label = { Text(stringResource(R.string.audit_custom_duration)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customMaxAttempts,
                onValueChange = viewModel::onCustomAttemptsChanged,
                label = { Text(stringResource(R.string.audit_custom_attempts)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = viewModel::applyCustomBudget) {
                Text(stringResource(R.string.audit_apply_budget))
            }
        }
    }
}

@Composable
private fun PlanSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.audit_plan_title), fontWeight = FontWeight.SemiBold)
            when {
                state.loadingPlan -> Text(stringResource(R.string.audit_plan_loading))
                state.planNotApplicableReason != null -> Text(state.planNotApplicableReason)
                state.plan != null -> {
                    Text(stringResource(R.string.audit_plan_auto_config), fontWeight = FontWeight.Medium)
                    state.novicePlanLines.forEach { Text("· $it") }
                    FeasibilityBlock(state.feasibilityRating)
                    TextButton(
                        onClick = { viewModel.setPlanDetailsExpanded(!state.planDetailsExpanded) },
                        modifier = Modifier.semantics { contentDescription = "Ver detalles" },
                    ) {
                        Text(
                            if (state.planDetailsExpanded) {
                                stringResource(R.string.audit_hide_details)
                            } else {
                                stringResource(R.string.audit_show_details)
                            },
                        )
                    }
                    if (state.planDetailsExpanded) {
                        state.explanation?.details?.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                        state.plan?.let { plan ->
                            Text(
                                stringResource(
                                    R.string.audit_plan_space,
                                    plan.totalCandidateSpace.toAbbreviatedString(),
                                    plan.budgetedAttemptCapacity?.toAbbreviatedString() ?: "—",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                else -> Text(stringResource(R.string.audit_plan_none))
            }
        }
    }
}

@Composable
private fun FeasibilityBlock(rating: FeasibilityRating?) {
    when (rating) {
        null -> Unit
        FeasibilityRating.Reasonable -> Unit
        FeasibilityRating.Expensive ->
            Text(
                stringResource(R.string.audit_feasibility_expensive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        FeasibilityRating.Impractical ->
            Text(
                stringResource(R.string.audit_feasibility_impractical),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        FeasibilityRating.Invalid ->
            Text(
                stringResource(R.string.audit_feasibility_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Composable
private fun ExecutionStatusCard(state: PasswordAuditUiState) {
    val metrics = state.metrics
    val progress = metrics?.processedPercentage?.toFloat()?.coerceIn(0f, 1f)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Estado de la auditoría" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(executionHeadline(state.searchState), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.audit_local_only_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (metrics != null) {
                MetricRow(stringResource(R.string.audit_metric_attempts), metrics.attempts.toExactString())
                MetricRow(stringResource(R.string.audit_metric_time), formatElapsed(metrics.elapsed.inWholeSeconds))
                MetricRow(
                    stringResource(R.string.audit_metric_speed),
                    "${(metrics.attemptsPerSecond / 1000).roundToInt()} k/s",
                )
                val stageCount = state.plan?.stages?.size
                if (stageCount != null && stageCount > 0) {
                    val stageIndex =
                        ((metrics.currentBucketIndex * stageCount) / metrics.totalBuckets.coerceAtLeast(1))
                            .coerceIn(0, stageCount - 1) + 1
                    MetricRow(
                        stringResource(R.string.audit_metric_stage),
                        stringResource(R.string.audit_metric_stage_of, stageIndex, stageCount),
                    )
                }
                metrics.processedPercentage?.let {
                    MetricRow(
                        stringResource(R.string.audit_metric_budget),
                        "${(it * 1000).roundToInt() / 10.0} %",
                    )
                }
                metrics.estimatedRemaining?.let {
                    MetricRow(
                        stringResource(R.string.audit_metric_remaining),
                        formatElapsed(it.inWholeSeconds),
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Text(stringResource(R.string.audit_preparing))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AuditResultReportCard(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    val report = state.resultReport
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Informe de auditoría" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audit_report_title), fontWeight = FontWeight.Bold)
            if (report != null) {
                Text(report.headline, fontWeight = FontWeight.SemiBold)
                Text(report.resistanceLabel, color = MaterialTheme.colorScheme.secondary)
                report.details.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                report.evidenceNotes.forEach { (quality, note) ->
                    Text(
                        "${evidenceLabel(quality)}: $note",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(resultHeadline(state))
                state.metrics?.let { m ->
                    MetricRow(stringResource(R.string.audit_metric_attempts), m.attempts.toExactString())
                    MetricRow(stringResource(R.string.audit_metric_time), formatElapsed(m.elapsed.inWholeSeconds))
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.errorDetails != null) {
                TextButton(onClick = viewModel::toggleErrorDetails) {
                    Text(
                        if (state.showErrorDetails) {
                            stringResource(R.string.audit_hide_details)
                        } else {
                            stringResource(R.string.audit_error_details)
                        },
                    )
                }
                if (state.showErrorDetails) {
                    Text(state.errorDetails, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun evidenceLabel(quality: EvidenceQuality): String =
    when (quality) {
        EvidenceQuality.Measured -> stringResource(R.string.audit_evidence_measured)
        EvidenceQuality.Estimated -> stringResource(R.string.audit_evidence_estimated)
        EvidenceQuality.Modelled -> stringResource(R.string.audit_evidence_modelled)
    }

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun executionHeadline(state: SearchState): String =
    when (state) {
        SearchState.Preparing -> "Preparando…"
        SearchState.Running -> "Auditando localmente…"
        SearchState.Cancelling -> "Deteniendo…"
        SearchState.Cancelled -> "Detenida"
        SearchState.Completed -> "Completada"
        SearchState.LimitReached -> "Límite alcanzado"
        SearchState.Failed -> "Error"
        SearchState.Idle -> ""
    }

private fun resultHeadline(state: PasswordAuditUiState): String =
    when (state.outcome) {
        SearchOutcome.Found -> "Contraseña encontrada (verificación local)."
        SearchOutcome.LimitReached -> "No se descubrió dentro del límite configurado."
        SearchOutcome.NotFound -> "Se agotó el espacio presupuestado sin descubrir la contraseña."
        SearchOutcome.Cancelled -> "Auditoría detenida por el usuario."
        SearchOutcome.Failed -> "La auditoría falló."
        null -> ""
    }

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun StrengthHint(summary: String) {
    Text(
        stringResource(R.string.audit_strength_hint, summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

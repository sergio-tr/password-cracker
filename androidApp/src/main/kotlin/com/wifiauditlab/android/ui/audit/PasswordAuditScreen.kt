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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.pluralStringResource
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
import com.wifiauditlab.android.ui.plan.SearchPlanExplainabilitySection
import com.wifiauditlab.android.ui.plan.toSearchPlanUiSummary
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.audit.PasswordSearchOutcomeKind
import com.wifiauditlab.core.math.CombinationCount
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
    val backCd = stringResource(R.string.navigate_back)
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
                            Text(it.message(), style = MaterialTheme.typography.bodySmall)
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
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(startLabel)
                            }
                            state.startBlockedReason?.let {
                                Text(
                                    it.message(),
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
                        Text(it.message(), color = MaterialTheme.colorScheme.error)
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
            Text(stringResource(familyLabelRes(state.securityFamily)))
            Text(
                networkMetaLine(state.wifiStandard, state.band),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.audit_connected_badge, stringResource(R.string.audit_connected_now)),
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
                val saveVaultCd = stringResource(R.string.audit_cd_save_vault)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = saveVaultCd },
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
            state.passwordError?.let { Text(it.message()) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    val modeAutoCd = stringResource(R.string.audit_cd_mode_auto)
    val modeAdvancedCd = stringResource(R.string.audit_cd_mode_advanced)
    val resetAutoCd = stringResource(R.string.audit_cd_reset_auto)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audit_mode), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == PasswordAuditInteractionMode.Automatic,
                onClick = { viewModel.selectMode(PasswordAuditInteractionMode.Automatic) },
                label = { Text(stringResource(R.string.audit_mode_automatic)) },
                modifier = Modifier.semantics { contentDescription = modeAutoCd },
            )
            FilterChip(
                selected = state.mode == PasswordAuditInteractionMode.Advanced,
                onClick = { viewModel.selectMode(PasswordAuditInteractionMode.Advanced) },
                label = { Text(stringResource(R.string.audit_mode_advanced)) },
                modifier = Modifier.semantics { contentDescription = modeAdvancedCd },
            )
        }
        if (state.mode == PasswordAuditInteractionMode.Advanced) {
            OutlinedButton(
                onClick = viewModel::resetToAutomaticDefaults,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = resetAutoCd },
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
                val presetCd = stringResource(R.string.audit_cd_preset, label)
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { viewModel.selectPreset(preset) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = presetCd },
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
    val showDetailsCd = stringResource(R.string.audit_cd_show_details)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.audit_plan_title), fontWeight = FontWeight.SemiBold)
            when {
                state.loadingPlan -> Text(stringResource(R.string.audit_plan_loading))
                state.planNotApplicableReason != null ->
                    Text(state.planNotApplicableReason.label())
                state.plan != null -> {
                    Text(stringResource(R.string.audit_plan_auto_config), fontWeight = FontWeight.Medium)
                    SearchPlanExplainabilitySection(
                        summary = state.plan.toSearchPlanUiSummary(),
                        stagesExpanded = state.searchStagesExpanded,
                        onToggleStagesExpanded = viewModel::toggleSearchStagesExpanded,
                    )
                    state.explanation?.noviceLines(state.plan)?.forEach { Text("· $it") }
                    FeasibilityBlock(state.feasibilityRating)
                    TextButton(
                        onClick = { viewModel.setPlanDetailsExpanded(!state.planDetailsExpanded) },
                        modifier = Modifier.semantics { contentDescription = showDetailsCd },
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
                        state.explanation?.details?.forEach {
                            Text("· ${it.line()}", style = MaterialTheme.typography.bodySmall)
                        }
                        state.plan.let { plan ->
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
    val statusCd = stringResource(R.string.audit_cd_status)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = statusCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(executionHeadline(state.searchState), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.audit_local_only_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (metrics != null) {
                MetricRow(
                    stringResource(R.string.audit_metric_attempts),
                    attemptsCountLabel(metrics.attempts),
                )
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
    val reportCd = stringResource(R.string.audit_cd_report)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = reportCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audit_report_title), fontWeight = FontWeight.Bold)
            if (report != null) {
                Text(report.headline.label(), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.audit_observed_resistance),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    report.passwordResistance.label().uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                report.performanceMetrics.forEach { metric ->
                    MetricRow(
                        metric.displayLabel(),
                        metric.displayValue(),
                    )
                }
                outcomeHint(report.searchOutcome)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.audit_split_config), fontWeight = FontWeight.SemiBold)
                Text(
                    report.networkConfig.displayLabel(state.securityFamily),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.audit_split_password), fontWeight = FontWeight.SemiBold)
                Text(
                    report.passwordResistance.label().uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (report.recommendations.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.audit_recommendations), fontWeight = FontWeight.SemiBold)
                    report.recommendations.forEach { Text("· ${it.label()}") }
                }

                OutlinedButton(
                    onClick = viewModel::toggleImproveGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.audit_how_to_improve))
                }
                if (state.improveGuideExpanded) {
                    Text("1. ${stringResource(R.string.audit_improve_step1)}")
                    Text("2. ${stringResource(R.string.audit_improve_step2)}")
                    Text("3. ${stringResource(R.string.audit_improve_step3)}")
                    Text("4. ${stringResource(R.string.audit_improve_step4)}")
                }

                TextButton(onClick = viewModel::toggleResultDetails) {
                    Text(
                        if (state.resultDetailsExpanded) {
                            stringResource(R.string.audit_hide_details)
                        } else {
                            stringResource(R.string.audit_show_details)
                        },
                    )
                }
                if (state.resultDetailsExpanded) {
                    report.classificationNotes.forEach {
                        Text("· ${it.line()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(resultHeadline(state))
                state.metrics?.let { m ->
                    MetricRow(
                        stringResource(R.string.audit_metric_attempts),
                        attemptsCountLabel(m.attempts),
                    )
                    MetricRow(stringResource(R.string.audit_metric_time), formatElapsed(m.elapsed.inWholeSeconds))
                }
            }
            state.errorMessage?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
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
private fun outcomeHint(outcome: PasswordSearchOutcomeKind): String? =
    when (outcome) {
        is PasswordSearchOutcomeKind.LimitReached ->
            stringResource(R.string.audit_limit_survived)
        is PasswordSearchOutcomeKind.Exhausted ->
            stringResource(R.string.audit_exhausted_model)
        is PasswordSearchOutcomeKind.Cancelled ->
            stringResource(R.string.audit_cancelled_incomplete)
        else -> null
    }

@Composable
private fun attemptsCountLabel(attempts: CombinationCount): String {
    val count = attempts.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    return if (count != null) {
        pluralStringResource(R.plurals.audit_attempts_count, count, count)
    } else {
        attempts.toExactString()
    }
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

@Composable
private fun executionHeadline(state: SearchState): String =
    when (state) {
        SearchState.Preparing -> stringResource(R.string.audit_state_preparing)
        SearchState.Running -> stringResource(R.string.audit_state_running)
        SearchState.Cancelling -> stringResource(R.string.audit_state_cancelling)
        SearchState.Cancelled -> stringResource(R.string.audit_state_cancelled)
        SearchState.Completed -> stringResource(R.string.audit_state_completed)
        SearchState.LimitReached -> stringResource(R.string.audit_state_limit)
        SearchState.Failed -> stringResource(R.string.audit_state_failed)
        SearchState.Idle -> ""
    }

@Composable
private fun resultHeadline(state: PasswordAuditUiState): String =
    when (state.outcome) {
        SearchOutcome.Found -> stringResource(R.string.audit_result_found)
        SearchOutcome.LimitReached -> stringResource(R.string.audit_result_limit)
        SearchOutcome.NotFound -> stringResource(R.string.audit_result_exhausted)
        SearchOutcome.Cancelled -> stringResource(R.string.audit_result_cancelled)
        SearchOutcome.Failed -> stringResource(R.string.audit_result_failed)
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

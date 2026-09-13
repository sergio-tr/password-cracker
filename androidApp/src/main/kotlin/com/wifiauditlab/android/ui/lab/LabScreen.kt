package com.wifiauditlab.android.ui.lab

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(viewModel: LabViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshNetworkContext()
    }
    val running =
        state.searchState == SearchState.Running ||
            state.searchState == SearchState.Preparing ||
            state.searchState == SearchState.Cancelling
    val cancelling = state.searchState == SearchState.Cancelling
    val startTestLabel = stringResource(R.string.lab_start_test)
    val startSearchLabel = stringResource(R.string.lab_start_search)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.lab_title)) }) },
        bottomBar = {
            LabActionBar(
                running = running,
                cancelling = cancelling,
                canStart = state.configErrorRes == null,
                startLabel = if (state.mode == LabInteractionMode.Guided) startTestLabel else startSearchLabel,
                onStart = viewModel::start,
                onStop = viewModel::stop,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            state.networkContext?.let { NetworkContextBanner(it) }
            if (state.secretMode == LabSecretMode.LocalPrototype) {
                LocalPrototypeBanner(state.prototype)
            }
            state.outcome?.let {
                ResultCard(
                    outcome = it,
                    found = state.foundCandidate,
                    errorMessage = state.errorMessage,
                    metrics = state.metrics,
                    showNoviceSummary =
                        state.mode == LabInteractionMode.Guided &&
                            state.secretMode == LabSecretMode.LocalPrototype &&
                            it in
                            setOf(
                                SearchOutcome.Found,
                                SearchOutcome.LimitReached,
                                SearchOutcome.Cancelled,
                            ),
                )
            }
            if (running) {
                ExecutionStatusCard(state = state)
            } else {
                if (state.mode == LabInteractionMode.Guided && state.secretMode == LabSecretMode.LocalPrototype) {
                    GuidedPrototypeCard(
                        state = state,
                        onSecretModeChange = viewModel::setSecretMode,
                        onPrototypeChange = viewModel::updatePrototype,
                        onPasswordChange = viewModel::onTargetPasswordChanged,
                        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                        onExpandAdvanced = { viewModel.setAdvancedExpanded(true) },
                        onCollapseAdvanced = { viewModel.setAdvancedExpanded(false) },
                        onOpenAdvancedMode = { viewModel.setMode(LabInteractionMode.Advanced) },
                    )
                } else {
                    SecretModeCard(
                        state = state,
                        onSecretModeChange = viewModel::setSecretMode,
                        onPrototypeChange = viewModel::updatePrototype,
                        onPasswordChange = viewModel::onTargetPasswordChanged,
                        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                    )
                    GuidedModeCard(
                        state = state,
                        onExpandAdvanced = { viewModel.setAdvancedExpanded(true) },
                        onCollapseAdvanced = { viewModel.setAdvancedExpanded(false) },
                        onResetGuided = viewModel::resetToGuidedDefaults,
                        onOpenAdvancedMode = { viewModel.setMode(LabInteractionMode.Advanced) },
                        onOpenGuidedMode = { viewModel.setMode(LabInteractionMode.Guided) },
                    )
                }
                if (state.mode == LabInteractionMode.Advanced || state.advancedExpanded) {
                    ConfigCard(state = state, enabled = true, onChange = viewModel::updateConfig)
                }
                if (state.mode == LabInteractionMode.Advanced ||
                    (state.advancedExpanded && state.secretMode != LabSecretMode.LocalPrototype)
                ) {
                    EstimatesCard(state)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LocalPrototypeBanner(prototype: LocalNetworkPrototype) {
    val localOnlyCd = stringResource(R.string.lab_cd_local_only_banner)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = localOnlyCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.lab_prototype_local_only), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.lab_prototype_local_only_body))
            if (prototype.ssidLabel.isNotBlank()) {
                Text(prototype.ssidLabel, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(familyLabelRes(prototype.securityFamily)))
            val meta =
                listOfNotNull(
                    prototype.wifiStandard?.let { standardLabel(it) },
                    prototype.band?.let { stringResource(bandDisplayLabelRes(it)) },
                ).joinToString(" · ").ifEmpty { null }
            meta?.let { Text(it) }
        }
    }
}

@Composable
private fun GuidedPrototypeCard(
    state: LabUiState,
    onSecretModeChange: (LabSecretMode) -> Unit,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onExpandAdvanced: () -> Unit,
    onCollapseAdvanced: () -> Unit,
    onOpenAdvancedMode: () -> Unit,
) {
    val guidedPrototypeCd = stringResource(R.string.lab_cd_guided_prototype)
    val advancedOptionsCd = stringResource(R.string.lab_cd_advanced_options)
    val advancedShow = stringResource(R.string.lab_advanced_show)
    val advancedHide = stringResource(R.string.lab_advanced_hide)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = guidedPrototypeCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.lab_guided_prototype_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.lab_guided_prototype_intro))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.secretMode == LabSecretMode.LocalPrototype,
                    onClick = { onSecretModeChange(LabSecretMode.LocalPrototype) },
                    label = { Text(stringResource(R.string.lab_mode_prototype)) },
                )
                FilterChip(
                    selected = state.secretMode == LabSecretMode.RandomHidden,
                    onClick = { onSecretModeChange(LabSecretMode.RandomHidden) },
                    label = { Text(stringResource(R.string.lab_mode_random)) },
                )
            }
            GuidedStep(
                step = 1,
                title = stringResource(R.string.lab_guided_step_ssid),
            ) {
                OutlinedTextField(
                    value = state.prototype.ssidLabel,
                    onValueChange = { onPrototypeChange(state.prototype.copy(ssidLabel = it)) },
                    label = { Text(stringResource(R.string.lab_prototype_ssid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GuidedStep(
                step = 2,
                title = stringResource(R.string.lab_guided_step_security),
            ) {
                GuidedSecurityPicker(
                    prototype = state.prototype,
                    onPrototypeChange = onPrototypeChange,
                )
            }
            GuidedStep(
                step = 3,
                title = stringResource(R.string.lab_guided_step_password),
            ) {
                PrototypePasswordField(
                    password = state.targetPassword,
                    passwordVisible = state.passwordVisible,
                    onPasswordChange = onPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                )
            }
            GuidedStep(
                step = 4,
                title = stringResource(R.string.lab_guided_step_start),
            ) {
                Text(stringResource(R.string.lab_guided_step_start_body))
            }
            state.configErrorRes?.let { Text(stringResource(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.lab_mode_guided)) },
                )
                FilterChip(
                    selected = false,
                    onClick = onOpenAdvancedMode,
                    label = { Text(stringResource(R.string.lab_mode_advanced)) },
                )
            }
            TextButton(
                onClick = { if (state.advancedExpanded) onCollapseAdvanced() else onExpandAdvanced() },
                modifier = Modifier.semantics { contentDescription = advancedOptionsCd },
            ) {
                Text(if (state.advancedExpanded) advancedHide else advancedShow)
            }
        }
    }
}

@Composable
private fun GuidedStep(
    step: Int,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.lab_guided_step_label, step, title), fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun GuidedSecurityPicker(
    prototype: LocalNetworkPrototype,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
) {
    var showSecondary by remember { mutableStateOf(prototype.securityFamily in LAB_SECONDARY_PSK_FAMILIES) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        LAB_PRIMARY_PSK_FAMILIES.forEach { family ->
            FilterChip(
                selected = prototype.securityFamily == family,
                onClick = { onPrototypeChange(prototype.copy(securityFamily = family)) },
                label = { Text(stringResource(familyLabelRes(family))) },
            )
        }
    }
    TextButton(onClick = { showSecondary = !showSecondary }) {
        Text(
            if (showSecondary) {
                stringResource(R.string.lab_guided_hide_legacy_security)
            } else {
                stringResource(R.string.lab_guided_show_legacy_security)
            },
        )
    }
    if (showSecondary) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            LAB_SECONDARY_PSK_FAMILIES.forEach { family ->
                FilterChip(
                    selected = prototype.securityFamily == family,
                    onClick = { onPrototypeChange(prototype.copy(securityFamily = family)) },
                    label = { Text(stringResource(familyLabelRes(family))) },
                )
            }
        }
    }
}

@Composable
private fun PrototypePasswordField(
    password: String,
    passwordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.lab_prototype_password)) },
        singleLine = true,
        visualTransformation =
            if (passwordVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription =
                        stringResource(
                            if (passwordVisible) {
                                R.string.lab_prototype_hide_password
                            } else {
                                R.string.lab_prototype_show_password
                            },
                        ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretModeCard(
    state: LabUiState,
    onSecretModeChange: (LabSecretMode) -> Unit,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
) {
    val secretModeCd = stringResource(R.string.lab_cd_secret_mode)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = secretModeCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lab_secret_mode_heading), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.secretMode == LabSecretMode.RandomHidden,
                    onClick = { onSecretModeChange(LabSecretMode.RandomHidden) },
                    label = { Text(stringResource(R.string.lab_mode_random)) },
                )
                FilterChip(
                    selected = state.secretMode == LabSecretMode.LocalPrototype,
                    onClick = { onSecretModeChange(LabSecretMode.LocalPrototype) },
                    label = { Text(stringResource(R.string.lab_mode_prototype)) },
                )
            }
            if (state.secretMode == LabSecretMode.LocalPrototype) {
                PrototypeForm(
                    prototype = state.prototype,
                    password = state.targetPassword,
                    passwordVisible = state.passwordVisible,
                    configErrorRes = state.configErrorRes,
                    onPrototypeChange = onPrototypeChange,
                    onPasswordChange = onPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                )
            }
        }
    }
}

@Composable
private fun PrototypeForm(
    prototype: LocalNetworkPrototype,
    password: String,
    passwordVisible: Boolean,
    configErrorRes: Int?,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = prototype.ssidLabel,
        onValueChange = { onPrototypeChange(prototype.copy(ssidLabel = it)) },
        label = { Text(stringResource(R.string.lab_prototype_ssid)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.lab_prototype_security), fontWeight = FontWeight.Medium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        LAB_PERSONAL_PSK_FAMILIES.forEach { family ->
            FilterChip(
                selected = prototype.securityFamily == family,
                onClick = { onPrototypeChange(prototype.copy(securityFamily = family)) },
                label = { Text(stringResource(familyLabelRes(family))) },
            )
        }
    }
    Text(stringResource(R.string.lab_prototype_standard), fontWeight = FontWeight.Medium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = prototype.wifiStandard == null,
            onClick = { onPrototypeChange(prototype.copy(wifiStandard = null)) },
            label = { Text(stringResource(R.string.lab_prototype_optional_none)) },
        )
        LAB_OPTIONAL_WIFI_STANDARDS.forEach { standard ->
            FilterChip(
                selected = prototype.wifiStandard == standard,
                onClick = { onPrototypeChange(prototype.copy(wifiStandard = standard)) },
                label = { Text(standardLabel(standard)) },
            )
        }
    }
    Text(stringResource(R.string.lab_prototype_band), fontWeight = FontWeight.Medium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = prototype.band == null,
            onClick = { onPrototypeChange(prototype.copy(band = null)) },
            label = { Text(stringResource(R.string.lab_prototype_optional_none)) },
        )
        LAB_OPTIONAL_WIFI_BANDS.forEach { band ->
            FilterChip(
                selected = prototype.band == band,
                onClick = { onPrototypeChange(prototype.copy(band = band)) },
                label = { Text(stringResource(bandDisplayLabelRes(band))) },
            )
        }
    }
    PrototypePasswordField(
        password = password,
        passwordVisible = passwordVisible,
        onPasswordChange = onPasswordChange,
        onTogglePasswordVisibility = onTogglePasswordVisibility,
    )
    configErrorRes?.let { Text(stringResource(it)) }
}

private val LAB_OPTIONAL_WIFI_STANDARDS =
    listOf(
        WifiStandard.WIFI_4,
        WifiStandard.WIFI_5,
        WifiStandard.WIFI_6,
        WifiStandard.WIFI_6E,
        WifiStandard.WIFI_7,
    )

private val LAB_OPTIONAL_WIFI_BANDS =
    listOf(
        WifiBand.GHZ_2_4,
        WifiBand.GHZ_5,
        WifiBand.GHZ_6,
    )

@Composable
private fun NetworkContextBanner(context: LabNetworkContext) {
    val networkContextCd = stringResource(R.string.lab_cd_network_context)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = networkContextCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.lab_heading), fontWeight = FontWeight.Bold)
            Text(context.displayName, fontWeight = FontWeight.SemiBold)
            if (context.ssidLabel != context.displayName) {
                Text(context.ssidLabel)
            }
            Text(stringResource(familyLabelRes(context.securityFamily)))
            val meta =
                listOfNotNull(
                    context.wifiStandard?.let { standardLabel(it) },
                    context.band?.let { stringResource(bandDisplayLabelRes(it)) },
                ).joinToString(" · ").ifEmpty { "—" }
            if (meta != "—") {
                Text(meta)
            }
            context.assessmentSummary?.let { Text(it) }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(context.guidedTitleRes()), fontWeight = FontWeight.SemiBold)
            Text(stringResource(context.securityFamily.guidedLabExplanationRes()))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.lab_simulation_local), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.lab_simulation_local_body))
        }
    }
}

@Composable
private fun GuidedModeCard(
    state: LabUiState,
    onExpandAdvanced: () -> Unit,
    onCollapseAdvanced: () -> Unit,
    onResetGuided: () -> Unit,
    onOpenAdvancedMode: () -> Unit,
    onOpenGuidedMode: () -> Unit,
) {
    val guidedModeCd = stringResource(R.string.lab_cd_guided_mode)
    val advancedOptionsCd = stringResource(R.string.lab_cd_advanced_options)
    val advancedShow = stringResource(R.string.lab_advanced_show)
    val advancedHide = stringResource(R.string.lab_advanced_hide)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = guidedModeCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(state.networkContext.guidedTitleRes()), fontWeight = FontWeight.Bold)
            if (state.networkContext == null) {
                Text(stringResource(R.string.lab_guided_auto_explanation))
            } else {
                Text(stringResource(state.networkContext.securityFamily.guidedLabExplanationRes()))
            }
            state.feasibility?.estimatedDurationRange?.let {
                Text(stringResource(R.string.lab_estimation, it.toApproximateString()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == LabInteractionMode.Guided,
                    onClick = onOpenGuidedMode,
                    label = { Text(stringResource(R.string.lab_mode_guided)) },
                )
                FilterChip(
                    selected = state.mode == LabInteractionMode.Advanced,
                    onClick = onOpenAdvancedMode,
                    label = { Text(stringResource(R.string.lab_mode_advanced)) },
                )
            }
            if (state.mode == LabInteractionMode.Guided) {
                TextButton(
                    onClick = { if (state.advancedExpanded) onCollapseAdvanced() else onExpandAdvanced() },
                    modifier = Modifier.semantics { contentDescription = advancedOptionsCd },
                ) {
                    Text(if (state.advancedExpanded) advancedHide else advancedShow)
                }
            } else {
                OutlinedButton(onClick = onResetGuided, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lab_reset_auto))
                }
            }
        }
    }
}

@Composable
private fun LabActionBar(
    running: Boolean,
    cancelling: Boolean,
    canStart: Boolean,
    startLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val stopSearchCd = stringResource(R.string.lab_cd_stop_search)
    val startSearchCd = stringResource(R.string.lab_cd_start_search)
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (running) {
                Button(
                    onClick = onStop,
                    enabled = !cancelling,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = stopSearchCd },
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stopSearchCd,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (cancelling) stringResource(R.string.lab_stopping) else stringResource(R.string.lab_stop))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = startSearchCd },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = startSearchCd)
                    Spacer(Modifier.size(8.dp))
                    Text(startLabel)
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusCard(state: LabUiState) {
    val metrics = state.metrics
    val progress = metrics?.processedPercentage?.toFloat()?.coerceIn(0f, 1f)
    val executionStatusCd = stringResource(R.string.lab_cd_execution_status)
    val progressBarCd = stringResource(R.string.lab_cd_progress_bar)
    val progressIndeterminateCd = stringResource(R.string.lab_cd_progress_indeterminate)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = executionStatusCd },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(executionHeadline(state.searchState), fontWeight = FontWeight.Bold)
            if (metrics != null) {
                MetricRow(stringResource(R.string.lab_metric_attempts), metrics.attempts.toExactString())
                MetricRow(stringResource(R.string.lab_metric_time), formatElapsed(metrics.elapsed.inWholeSeconds))
                MetricRow(
                    stringResource(R.string.lab_metric_speed),
                    stringResource(
                        R.string.lab_metric_speed_value,
                        (metrics.attemptsPerSecond / 1000).roundToInt(),
                    ),
                )
                metrics.processedPercentage?.let {
                    MetricRow(
                        stringResource(R.string.lab_metric_progress),
                        stringResource(
                            R.string.lab_metric_progress_value,
                            (it * 1000).roundToInt() / 10.0,
                        ),
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = progressBarCd },
                    )
                } else {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = progressIndeterminateCd },
                    )
                }
            } else {
                Text(stringResource(R.string.lab_preparing_search))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
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
        SearchState.Preparing -> stringResource(R.string.lab_state_preparing)
        SearchState.Running -> stringResource(R.string.lab_state_running)
        SearchState.Cancelling -> stringResource(R.string.lab_state_cancelling)
        else -> state.name
    }

@Composable
private fun ConfigCard(
    state: LabUiState,
    enabled: Boolean,
    onChange: (LabConfig) -> Unit,
) {
    val config = state.config
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lab_challenge), fontWeight = FontWeight.SemiBold)
            Text(stringResource(config.alphabet.labelRes))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                AlphabetChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = config.alphabet == choice,
                        onClick = { if (enabled) onChange(config.copy(alphabet = choice)) },
                        label = { Text(stringResource(choice.labelRes)) },
                    )
                }
            }
            Text(stringResource(R.string.lab_strategy, stringResource(config.strategy.labelRes)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                StrategyChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = config.strategy == choice,
                        onClick = { if (enabled) onChange(config.copy(strategy = choice)) },
                        label = { Text(stringResource(choice.labelRes)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lab_secret_length, config.secretLength))
                OutlinedButton(
                    onClick = { if (enabled && config.secretLength > 1) onChange(config.copy(secretLength = config.secretLength - 1)) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("-") }
                OutlinedButton(
                    onClick = { if (enabled && config.secretLength < 12) onChange(config.copy(secretLength = config.secretLength + 1)) },
                ) { Text("+") }
            }
            OutlinedTextField(
                value = config.maxAttempts?.toString().orEmpty(),
                onValueChange = { value ->
                    if (enabled) onChange(config.copy(maxAttempts = value.trim().toLongOrNull()))
                },
                label = { Text(stringResource(R.string.lab_attempt_limit)) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.lab_workers, config.workers))
            Text(
                stringResource(R.string.lab_workers_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(1, 2, 4).forEach { count ->
                    FilterChip(
                        selected = config.workers == count,
                        onClick = { if (enabled) onChange(config.copy(workers = count)) },
                        label = { Text("$count") },
                    )
                }
            }
            OutlinedTextField(
                value = config.maxDurationSeconds?.toString().orEmpty(),
                onValueChange = { value ->
                    if (enabled) onChange(config.copy(maxDurationSeconds = value.trim().toLongOrNull()))
                },
                label = { Text(stringResource(R.string.lab_time_limit)) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.configErrorRes?.let { Text(stringResource(it)) }
        }
    }
}

@Composable
private fun EstimatesCard(state: LabUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.lab_before_start), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.lab_before_challenge,
                    stringResource(state.config.alphabet.labelRes),
                    state.effectiveSecretLength,
                ),
            )
            Text(stringResource(R.string.lab_strategy, stringResource(state.config.strategy.labelRes)))
            Text(stringResource(R.string.lab_estimated_combinations, state.estimatedCombinations.toAbbreviatedString()))
            Text(
                stringResource(
                    R.string.lab_time_limit_value,
                    state.config.maxDurationSeconds?.let { "$it s" } ?: "—",
                ),
            )
            Text(
                stringResource(
                    R.string.lab_attempt_limit_value,
                    state.config.maxAttempts?.toString() ?: "—",
                ),
            )
            state.feasibility?.let { feasibility ->
                Text(stringResource(R.string.lab_feasibility, feasibilityLabel(feasibility.rating)))
                feasibility.estimatedDurationRange?.let {
                    Text(stringResource(R.string.lab_estimation, it.toApproximateString()))
                }
                Text(feasibility.reason)
            }
        }
    }
}

@Composable
private fun ResultCard(
    outcome: SearchOutcome,
    found: String?,
    errorMessage: String?,
    metrics: SearchMetrics?,
    showNoviceSummary: Boolean = false,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(outcomeLabel(outcome), fontWeight = FontWeight.Bold)
            if (showNoviceSummary) {
                Text(
                    stringResource(R.string.lab_novice_observed_resistance, outcomeLabel(outcome)),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.lab_novice_local_only_reminder))
            }
            if (outcome == SearchOutcome.Found && found != null) {
                Text(stringResource(R.string.lab_found_secret, found))
            }
            errorMessage?.let { Text(it) }
            metrics?.let {
                Text(stringResource(R.string.lab_final_attempts, it.attempts.toExactString()))
                Text(stringResource(R.string.lab_final_time, formatElapsed(it.elapsed.inWholeSeconds)))
            }
        }
    }
}

@Composable
private fun feasibilityLabel(rating: FeasibilityRating): String =
    when (rating) {
        FeasibilityRating.Reasonable -> stringResource(R.string.lab_feasibility_reasonable)
        FeasibilityRating.Expensive -> stringResource(R.string.lab_feasibility_expensive)
        FeasibilityRating.Impractical -> stringResource(R.string.lab_feasibility_impractical)
        FeasibilityRating.Invalid -> stringResource(R.string.lab_feasibility_invalid)
    }

@Composable
private fun outcomeLabel(outcome: SearchOutcome): String =
    when (outcome) {
        SearchOutcome.Found -> stringResource(R.string.lab_outcome_found)
        SearchOutcome.NotFound -> stringResource(R.string.lab_outcome_not_found)
        SearchOutcome.LimitReached -> stringResource(R.string.lab_outcome_limit)
        SearchOutcome.Cancelled -> stringResource(R.string.lab_outcome_cancelled)
        SearchOutcome.Failed -> stringResource(R.string.lab_outcome_failed)
    }

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

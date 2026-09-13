package com.wifiauditlab.android.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auditoría rápida") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Atrás") }
                },
            )
        },
        bottomBar = {
            if (!state.missingTarget) {
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
                                    .semantics { contentDescription = "Detener auditoría" },
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Text(
                                if (state.searchState == SearchState.Cancelling) {
                                    "Deteniendo…"
                                } else {
                                    "DETENER"
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
                                    .semantics { contentDescription = "Iniciar auditoría" },
                        ) {
                            Text("INICIAR AUDITORÍA")
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
                state.missingTarget -> Text("No hay una red seleccionada para auditar.")
                else -> {
                    NetworkBanner(state)
                    if (state.isActive || state.outcome != null) {
                        ExecutionStatusCard(state)
                    }
                    if (state.outcome != null && !state.isActive) {
                        BasicResultCard(state)
                    }
                    if (!state.isActive) {
                        PasswordSection(state, viewModel)
                        PresetSection(state, viewModel)
                        if (state.advancedExpanded || state.preset == PasswordAuditBudgetPreset.Custom) {
                            AdvancedBudgetSection(state, viewModel)
                        }
                    }
                    PlanSection(state)
                    state.strength?.let { StrengthHint(it.summary) }
                }
            }
        }
    }
}

@Composable
private fun NetworkBanner(state: PasswordAuditUiState) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Red conectada", style = MaterialTheme.typography.labelMedium)
            Text(state.displayName, fontWeight = FontWeight.Bold)
            if (state.ssidLabel.isNotBlank() && state.ssidLabel != state.displayName) {
                Text(state.ssidLabel)
            }
            Text(state.familyLabel)
            Text(state.metaLine, style = MaterialTheme.typography.bodySmall)
            Text(
                "La búsqueda es solo local: no se autentica contra el router.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Contraseña conocida", fontWeight = FontWeight.SemiBold)
            Text(
                "Introduce la contraseña de esta red o recupérala del Vault. " +
                    "No se usa para priorizar la búsqueda automática.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = viewModel::onPasswordChanged,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Contraseña conocida" },
                label = { Text("Contraseña") },
                singleLine = true,
                isError = state.passwordError != null,
                visualTransformation =
                    if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                supportingText = {
                    when {
                        state.passwordError != null -> Text(state.passwordError)
                        state.passwordFromVault -> Text("Recuperada del Vault (solo en memoria)")
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::togglePasswordVisibility) {
                    Text(if (state.passwordVisible) "Ocultar" else "Mostrar")
                }
                if (state.passwordInput.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearPassword) { Text("Borrar") }
                }
                if (state.vaultSecretAvailable) {
                    OutlinedButton(
                        onClick = viewModel::useVaultPassword,
                        modifier = Modifier.semantics { contentDescription = "Usar del Vault" },
                    ) {
                        Text("Usar del Vault")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presupuesto", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PasswordAuditBudgetPreset.Quick,
                PasswordAuditBudgetPreset.Standard,
                PasswordAuditBudgetPreset.Deep,
                PasswordAuditBudgetPreset.Custom,
            ).forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { viewModel.selectPreset(preset) },
                    label = { Text(preset.chipLabel()) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Preset ${preset.chipLabel()}"
                        },
                )
            }
        }
        TextButton(
            onClick = { viewModel.setAdvancedExpanded(!state.advancedExpanded) },
            modifier = Modifier.semantics { contentDescription = "Opciones avanzadas" },
        ) {
            Text(if (state.advancedExpanded) "Ocultar opciones avanzadas" else "Opciones avanzadas")
        }
    }
}

@Composable
private fun AdvancedBudgetSection(
    state: PasswordAuditUiState,
    viewModel: PasswordAuditViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Límites personalizados", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.customDurationSeconds,
                onValueChange = viewModel::onCustomDurationChanged,
                label = { Text("Duración máxima (segundos)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customMaxAttempts,
                onValueChange = viewModel::onCustomAttemptsChanged,
                label = { Text("Intentos máximos (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = viewModel::applyCustomBudget) {
                Text("Aplicar presupuesto")
            }
        }
    }
}

@Composable
private fun PlanSection(state: PasswordAuditUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Plan automático", fontWeight = FontWeight.SemiBold)
            when {
                state.loadingPlan -> Text("Calculando plan…")
                state.planNotApplicableReason != null -> Text(state.planNotApplicableReason)
                state.explanation != null -> {
                    Text(state.explanation.headline, fontWeight = FontWeight.Medium)
                    state.explanation.details.forEach { Text("· $it") }
                    state.feasibilityRating?.let { rating ->
                        Text(
                            feasibilityLabel(rating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    state.plan?.let { plan ->
                        Text(
                            "Espacio total: ${plan.totalCandidateSpace.toAbbreviatedString()} · " +
                                "Presupuestado: ${plan.budgetedAttemptCapacity?.toAbbreviatedString() ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                else -> Text("Sin plan.")
            }
        }
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
                "Verificación local — no se autentica contra el router.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (metrics != null) {
                MetricRow("Intentos", metrics.attempts.toExactString())
                MetricRow("Tiempo", formatElapsed(metrics.elapsed.inWholeSeconds))
                MetricRow("Velocidad", "${(metrics.attemptsPerSecond / 1000).roundToInt()} k/s")
                metrics.processedPercentage?.let {
                    MetricRow("Progreso", "${(it * 1000).roundToInt() / 10.0} %")
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
                Text("Preparando…")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BasicResultCard(state: PasswordAuditUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Resultado", fontWeight = FontWeight.Bold)
            Text(resultHeadline(state))
            state.metrics?.let { m ->
                MetricRow("Intentos", m.attempts.toExactString())
                MetricRow("Tiempo", formatElapsed(m.elapsed.inWholeSeconds))
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

private fun executionHeadline(state: SearchState): String =
    when (state) {
        SearchState.Preparing -> "Preparando…"
        SearchState.Running -> "Auditando localmente…"
        SearchState.Cancelling -> "Deteniendo…"
        else -> state.name
    }

private fun resultHeadline(state: PasswordAuditUiState): String =
    when (state.outcome) {
        SearchOutcome.Found ->
            "La contraseña conocida se descubrió dentro del presupuesto (verificación local)."
        SearchOutcome.LimitReached ->
            "No se descubrió dentro del límite configurado."
        SearchOutcome.NotFound ->
            "Se agotó el espacio presupuestado sin descubrir la contraseña."
        SearchOutcome.Cancelled -> "Auditoría detenida por el usuario."
        SearchOutcome.Failed -> "La auditoría falló."
        null -> ""
    }

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun StrengthHint(summary: String) {
    Text(
        "Análisis estructural (independiente del plan): $summary",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun feasibilityLabel(rating: FeasibilityRating): String =
    when (rating) {
        FeasibilityRating.Reasonable -> "Viabilidad del presupuesto: razonable"
        FeasibilityRating.Expensive -> "Viabilidad del presupuesto: costosa"
        FeasibilityRating.Impractical ->
            "Viabilidad del presupuesto: poco práctica (el límite detiene la búsqueda)"
        FeasibilityRating.Invalid -> "Viabilidad: inválida"
    }

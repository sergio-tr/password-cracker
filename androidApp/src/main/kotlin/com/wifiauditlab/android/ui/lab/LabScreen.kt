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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val running =
        state.searchState == SearchState.Running ||
            state.searchState == SearchState.Preparing ||
            state.searchState == SearchState.Cancelling
    val cancelling = state.searchState == SearchState.Cancelling

    Scaffold(
        topBar = { TopAppBar(title = { Text("Laboratorio sintético") }) },
        bottomBar = {
            LabActionBar(
                running = running,
                cancelling = cancelling,
                canStart = state.configError == null,
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
            // Outcome first so Cancelled/Found remain visible without scrolling past config.
            state.outcome?.let { ResultCard(it, state.foundCandidate, state.errorMessage, state.metrics) }
            if (running) {
                ExecutionStatusCard(state = state)
            } else {
                ConfigCard(state = state, enabled = true, onChange = viewModel::updateConfig)
                EstimatesCard(state)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Fixed action zone — always visible without scrolling.
 * STOP stays reachable for every cancelable state.
 */
@Composable
private fun LabActionBar(
    running: Boolean,
    cancelling: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (running) {
                Button(
                    onClick = onStop,
                    enabled = !cancelling,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Detener búsqueda" },
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (cancelling) "Deteniendo…" else "DETENER")
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Iniciar búsqueda" },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Iniciar búsqueda")
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusCard(state: LabUiState) {
    val metrics = state.metrics
    val progress = metrics?.processedPercentage?.toFloat()?.coerceIn(0f, 1f)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Estado de la búsqueda" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(executionHeadline(state.searchState), fontWeight = FontWeight.Bold)
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Barra de progreso de la búsqueda" },
                    )
                } else {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Barra de progreso indeterminada" },
                    )
                }
            } else {
                Text("Preparando la búsqueda…")
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

private fun executionHeadline(state: SearchState): String =
    when (state) {
        SearchState.Preparing -> "Preparando…"
        SearchState.Running -> "Buscando…"
        SearchState.Cancelling -> "Deteniendo…"
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
            Text("Reto", fontWeight = FontWeight.SemiBold)
            Text(config.alphabet.label)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                AlphabetChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = config.alphabet == choice,
                        onClick = { if (enabled) onChange(config.copy(alphabet = choice)) },
                        label = { Text(choice.label) },
                    )
                }
            }
            Text("Estrategia: ${config.strategy.label}")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                StrategyChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = config.strategy == choice,
                        onClick = { if (enabled) onChange(config.copy(strategy = choice)) },
                        label = { Text(choice.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Longitud del secreto: ${config.secretLength}")
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
                label = { Text("Límite de intentos") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Workers: ${config.workers} (1 es la referencia)")
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
                label = { Text("Límite de tiempo (s)") },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.configError?.let { Text(it) }
        }
    }
}

@Composable
private fun EstimatesCard(state: LabUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Antes de iniciar", fontWeight = FontWeight.SemiBold)
            Text("Reto: ${state.config.alphabet.label} · longitud ${state.config.secretLength}")
            Text("Estrategia: ${state.config.strategy.label}")
            Text("Combinaciones estimadas: ${state.estimatedCombinations.toAbbreviatedString()}")
            Text("Límite de tiempo: ${state.config.maxDurationSeconds?.let { "$it s" } ?: "—"}")
            Text("Límite de intentos: ${state.config.maxAttempts ?: "—"}")
            state.feasibility?.let { feasibility ->
                Text("Viabilidad: ${feasibilityLabel(feasibility.rating)}")
                feasibility.estimatedDurationRange?.let { Text("Estimación: ${it.toApproximateString()}") }
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
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(outcomeLabel(outcome), fontWeight = FontWeight.Bold)
            if (outcome == SearchOutcome.Found && found != null) {
                Text("Secreto encontrado: $found")
            }
            errorMessage?.let { Text(it) }
            metrics?.let {
                Text("Intentos finales: ${it.attempts.toExactString()}")
                Text("Tiempo final: ${formatElapsed(it.elapsed.inWholeSeconds)}")
            }
        }
    }
}

private fun feasibilityLabel(rating: FeasibilityRating): String =
    when (rating) {
        FeasibilityRating.Reasonable -> "Razonable"
        FeasibilityRating.Expensive -> "Costosa"
        FeasibilityRating.Impractical -> "Impracticable"
        FeasibilityRating.Invalid -> "No válida"
    }

private fun outcomeLabel(outcome: SearchOutcome): String =
    when (outcome) {
        SearchOutcome.Found -> "ENCONTRADO"
        SearchOutcome.NotFound -> "NO ENCONTRADO"
        SearchOutcome.LimitReached -> "LÍMITE ALCANZADO"
        SearchOutcome.Cancelled -> "CANCELADO"
        SearchOutcome.Failed -> "ERROR"
    }

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

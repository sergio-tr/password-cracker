package com.wifiauditlab.android.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    Scaffold(topBar = { TopAppBar(title = { Text("Laboratorio sintético") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConfigCard(state = state, enabled = !running, onChange = viewModel::updateConfig)
            EstimatesCard(state)

            if (running) {
                Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) { Text("STOP") }
            } else {
                Button(
                    onClick = viewModel::start,
                    enabled = state.configError == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Iniciar búsqueda") }
            }

            state.metrics?.let { MetricsCard(state.searchState, it) }
            state.outcome?.let { ResultCard(it, state.foundCandidate) }
        }
    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlphabetChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = config.alphabet == choice,
                        onClick = { if (enabled) onChange(config.copy(alphabet = choice)) },
                        label = { Text(choice.name) },
                    )
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Longitud del secreto: ${config.secretLength}")
                OutlinedButton(
                    onClick = { if (enabled && config.secretLength > 1) onChange(config.copy(secretLength = config.secretLength - 1)) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("-") }
                OutlinedButton(
                    onClick = { if (enabled && config.secretLength < 12) onChange(config.copy(secretLength = config.secretLength + 1)) },
                ) { Text("+") }
            }
            Text("Límite de intentos: ${config.maxAttempts ?: "—"}")
            Text("Límite de tiempo: ${config.maxDurationSeconds?.let { "$it s" } ?: "—"}")
            state.configError?.let { Text(it) }
        }
    }
}

@Composable
private fun EstimatesCard(state: LabUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Estimaciones", fontWeight = FontWeight.SemiBold)
            Text("Combinaciones: ${state.estimatedCombinations.toAbbreviatedString()}")
            state.feasibility?.let { feasibility ->
                Text("Viabilidad: ${feasibilityLabel(feasibility.rating)}")
                Text(feasibility.reason)
            }
        }
    }
}

@Composable
private fun MetricsCard(
    searchState: SearchState,
    metrics: SearchMetrics,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(searchState.name.uppercase(), fontWeight = FontWeight.Bold)
            Text("Intentos: ${metrics.attempts.toExactString()}")
            Text("Tiempo: ${formatElapsed(metrics.elapsed.inWholeSeconds)}")
            Text("Velocidad: ${(metrics.attemptsPerSecond / 1000).roundToInt()} k/s")
            metrics.processedPercentage?.let { Text("Progreso: ${(it * 100).roundToInt() / 100.0} %") }
            Text("Fase: ${metrics.currentBucketIndex + 1} / ${metrics.totalBuckets}")
        }
    }
}

@Composable
private fun ResultCard(
    outcome: SearchOutcome,
    found: String?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(outcomeLabel(outcome), fontWeight = FontWeight.Bold)
            if (outcome == SearchOutcome.Found && found != null) {
                Text("Secreto encontrado: $found")
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

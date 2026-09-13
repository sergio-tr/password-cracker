package com.wifiauditlab.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    calibrationViewModel: SettingsCalibrationViewModel = koinViewModel(),
    benchmarkViewModel: SettingsBenchmarkViewModel = koinViewModel(),
) {
    val capabilities = AndroidPlatformCapabilities()
    val calibration by calibrationViewModel.state.collectAsStateWithLifecycle()
    val benchmarks by benchmarkViewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permisos y servicios", fontWeight = FontWeight.SemiBold)
                    Text("Revisa permisos requeridos, servicios del sistema y capacidades opcionales.")
                    Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir centro de permisos")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Onboarding", fontWeight = FontWeight.SemiBold)
                    Text("Vuelve a ver la introducción de Cercanas, Vault y Laboratorio.")
                    OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text("Mostrar introducción")
                    }
                }
            }
            CalibrationCard(
                state = calibration,
                onRecalibrate = calibrationViewModel::recalibrate,
            )
            BenchmarksCard(
                state = benchmarks,
                onRunSuite = benchmarkViewModel::runSuite,
                onCompare = benchmarkViewModel::compareBaselineVsParallel,
                onClear = benchmarkViewModel::clearHistory,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Capacidades de la plataforma", fontWeight = FontWeight.SemiBold)
                    Text("Descubrimiento de redes cercanas: ${capabilities.nearbyWifiDiscovery.toYesNo()}")
                    Text("Inspección de la red actual: ${capabilities.currentWifiInspection.toYesNo()}")
                    Text("Almacenamiento seguro de secretos: ${capabilities.secureSecretStorage.toYesNo()}")
                    Text("Geolocalización: ${capabilities.geolocation.toYesNo()}")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qué hace esta app", fontWeight = FontWeight.SemiBold)
                    Text("Cercanas — descubre redes Wi‑Fi reales y evalúa su seguridad.")
                    Text("Guardadas — el Vault: alias, ubicación y contraseña cifrada.")
                    Text("Laboratorio — busca secretos sintéticos con límites y un botón STOP.")
                    Text(
                        "El laboratorio está aislado de las redes reales: sus algoritmos nunca se conectan a un Wi‑Fi.",
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarksCard(
    state: BenchmarkSettingsUiState,
    onRunSuite: () -> Unit,
    onCompare: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lab benchmarking", fontWeight = FontWeight.SemiBold)
            Text("Estado: ${state.statusLabel}")
            state.comparison?.let { comparison ->
                Text("Baseline throughput: ${formatRate(comparison.baselineThroughput)}")
                Text("Parallel throughput: ${formatRate(comparison.parallelThroughput)}")
                Text("Speedup: ${comparison.speedup?.let { String.format("%.2fx", it) } ?: "—"}")
                Text(
                    "Worker efficiency: ${comparison.workerEfficiency?.let { String.format("%.2f", it) } ?: "—"}",
                )
            }
            if (state.history.isNotEmpty()) {
                Text("Historical runs (latest ${state.history.take(5).size}):")
                state.history.take(5).forEach { run ->
                    Text(
                        "· ${run.challengeProfile} · w=${run.workerCount} · " +
                            "${formatRate(run.attemptsPerSecond)} · ${run.terminalResult}",
                    )
                }
            }
            state.exportJson?.let {
                Text("Export JSON ready (${it.length} chars, sanitized — no secrets).")
            }
            state.exportCsv?.let {
                Text("Export CSV ready (${it.lineSequence().count()} lines, sanitized).")
            }
            state.errorMessage?.let { Text(it) }
            Button(
                onClick = onRunSuite,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.running) "Running…" else "Run benchmark suite") }
            OutlinedButton(
                onClick = onCompare,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Compare baseline vs 4 workers") }
            OutlinedButton(
                onClick = onClear,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear benchmark history") }
        }
    }
}

private fun formatRate(value: Double?): String =
    when {
        value == null -> "—"
        value >= 1_000_000 -> String.format("%.2f M/s", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1f k/s", value / 1_000.0)
        else -> String.format("%.0f /s", value)
    }

@Composable
private fun CalibrationCard(
    state: CalibrationSettingsUiState,
    onRecalibrate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Performance calibration", fontWeight = FontWeight.SemiBold)
            Text("Estado: ${state.statusLabel}")
            Text("Last calibration: ${state.lastCalibrationLabel}")
            Text("Measured throughput: ${state.throughputLabel}")
            Text("Sample duration: ${state.sampleDurationLabel}")
            Text("Fingerprint: ${state.fingerprintLabel}")
            state.errorMessage?.let { Text(it) }
            Button(
                onClick = onRecalibrate,
                enabled = !state.loading && !state.recalibrating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.recalibrating) "Recalibrating…" else "Recalibrate")
            }
        }
    }
}

private fun Boolean.toYesNo(): String = if (this) "Sí" else "No"

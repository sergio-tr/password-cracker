package com.wifiauditlab.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.android.i18n.AppLanguagePreferences
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    calibrationViewModel: SettingsCalibrationViewModel = koinViewModel(),
    benchmarkViewModel: SettingsBenchmarkViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val currentLanguage = AppLanguagePreferences.currentTag(context)
    val yesLabel = stringResource(R.string.settings_yes)
    val noLabel = stringResource(R.string.settings_no)
    val capabilities = AndroidPlatformCapabilities()
    val calibration by calibrationViewModel.state.collectAsStateWithLifecycle()
    val benchmarks by benchmarkViewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
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
                    Text(stringResource(R.string.settings_language), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_language_help))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChip(
                            selected = currentLanguage == AppLanguagePreferences.SYSTEM,
                            label = stringResource(R.string.settings_language_system),
                            onClick = { AppLanguagePreferences.apply(context, AppLanguagePreferences.SYSTEM) },
                        )
                        LanguageChip(
                            selected = currentLanguage == AppLanguagePreferences.SPANISH,
                            label = stringResource(R.string.settings_language_es),
                            onClick = { AppLanguagePreferences.apply(context, AppLanguagePreferences.SPANISH) },
                        )
                        LanguageChip(
                            selected = currentLanguage == AppLanguagePreferences.ENGLISH,
                            label = stringResource(R.string.settings_language_en),
                            onClick = { AppLanguagePreferences.apply(context, AppLanguagePreferences.ENGLISH) },
                        )
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_permissions), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_permissions_help))
                    Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_open_permissions))
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_onboarding), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_onboarding_help))
                    OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_show_onboarding))
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
                    Text(stringResource(R.string.settings_capabilities), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            R.string.settings_cap_nearby,
                            capabilities.nearbyWifiDiscovery.toYesNo(yesLabel, noLabel),
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.settings_cap_current,
                            capabilities.currentWifiInspection.toYesNo(yesLabel, noLabel),
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.settings_cap_secrets,
                            capabilities.secureSecretStorage.toYesNo(yesLabel, noLabel),
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.settings_cap_geo,
                            capabilities.geolocation.toYesNo(yesLabel, noLabel),
                        ),
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_about), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_about_nearby))
                    Text(stringResource(R.string.settings_about_vault))
                    Text(stringResource(R.string.settings_about_lab))
                    Text(stringResource(R.string.settings_about_audit))
                    Text(stringResource(R.string.settings_about_engine))
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
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
            Text(stringResource(R.string.settings_bench_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_bench_status, state.statusLabel))
            state.comparison?.let { comparison ->
                Text(stringResource(R.string.settings_bench_baseline, formatRate(comparison.baselineThroughput)))
                Text(stringResource(R.string.settings_bench_parallel, formatRate(comparison.parallelThroughput)))
                Text(
                    stringResource(
                        R.string.settings_bench_speedup,
                        comparison.speedup?.let { String.format("%.2fx", it) } ?: "—",
                    ),
                )
                Text(
                    stringResource(
                        R.string.settings_bench_efficiency,
                        comparison.workerEfficiency?.let { String.format("%.2f", it) } ?: "—",
                    ),
                )
            }
            if (state.history.isNotEmpty()) {
                Text(stringResource(R.string.settings_bench_history, state.history.take(5).size))
                state.history.take(5).forEach { run ->
                    Text(
                        stringResource(
                            R.string.settings_bench_run_line,
                            run.challengeProfile,
                            run.workerCount,
                            formatRate(run.attemptsPerSecond),
                            run.terminalResult,
                        ),
                    )
                }
            }
            state.exportJson?.let {
                Text(stringResource(R.string.settings_bench_export_json, it.length))
            }
            state.exportCsv?.let {
                Text(stringResource(R.string.settings_bench_export_csv, it.lineSequence().count()))
            }
            state.errorMessage?.let { Text(it) }
            Button(
                onClick = onRunSuite,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.running) {
                        stringResource(R.string.settings_bench_running)
                    } else {
                        stringResource(R.string.settings_bench_run_suite)
                    },
                )
            }
            OutlinedButton(
                onClick = onCompare,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_bench_compare)) }
            OutlinedButton(
                onClick = onClear,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_bench_clear)) }
        }
    }
}

@Composable
private fun formatRate(value: Double?): String =
    when {
        value == null -> "—"
        value >= 1_000_000 -> stringResource(R.string.settings_bench_rate_m, value / 1_000_000.0)
        value >= 1_000 -> stringResource(R.string.settings_bench_rate_k, value / 1_000.0)
        else -> stringResource(R.string.settings_bench_rate_raw, value)
    }

@Composable
private fun CalibrationCard(
    state: CalibrationSettingsUiState,
    onRecalibrate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_cal_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_cal_status, state.statusLabel))
            Text(stringResource(R.string.settings_cal_last, state.lastCalibrationLabel))
            Text(stringResource(R.string.settings_cal_throughput, state.throughputLabel))
            Text(stringResource(R.string.settings_cal_sample, state.sampleDurationLabel))
            Text(stringResource(R.string.settings_cal_fingerprint, state.fingerprintLabel))
            state.errorMessage?.let { Text(it) }
            Button(
                onClick = onRecalibrate,
                enabled = !state.loading && !state.recalibrating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.recalibrating) {
                        stringResource(R.string.settings_cal_recalibrating)
                    } else {
                        stringResource(R.string.settings_cal_recalibrate)
                    },
                )
            }
        }
    }
}

private fun Boolean.toYesNo(
    yes: String,
    no: String,
): String = if (this) yes else no

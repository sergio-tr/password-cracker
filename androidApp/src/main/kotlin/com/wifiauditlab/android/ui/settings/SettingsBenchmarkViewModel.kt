package com.wifiauditlab.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.android.R
import com.wifiauditlab.android.platform.SharedPreferencesBenchmarkRepository
import com.wifiauditlab.android.ui.audit.UiStrings
import com.wifiauditlab.lab.domain.BenchmarkComparison
import com.wifiauditlab.lab.domain.BenchmarkRecord
import com.wifiauditlab.lab.domain.engine.LabBenchmarkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkSettingsUiState(
    val running: Boolean = false,
    val history: List<BenchmarkRecord> = emptyList(),
    val comparison: BenchmarkComparison? = null,
    val exportJson: String? = null,
    val exportCsv: String? = null,
    val statusLabel: String = "Sin ejecuciones",
    val errorMessage: String? = null,
)

class SettingsBenchmarkViewModel(
    private val benchmarks: LabBenchmarkService,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val _state = MutableStateFlow(BenchmarkSettingsUiState())
    val state: StateFlow<BenchmarkSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val history = benchmarks.history()
            _state.update {
                it.copy(
                    history = history,
                    statusLabel =
                        if (history.isEmpty()) {
                            uiStrings.get(R.string.settings_bench_none)
                        } else {
                            uiStrings.get(R.string.settings_bench_runs, history.size)
                        },
                    errorMessage = null,
                )
            }
        }
    }

    fun runSuite() {
        viewModelScope.launch {
            _state.update { it.copy(running = true, errorMessage = null) }
            runCatching { benchmarks.runSuite() }
                .onSuccess { records ->
                    _state.update {
                        it.copy(
                            running = false,
                            history = records + it.history,
                            statusLabel = uiStrings.get(R.string.settings_bench_suite, records.size),
                            exportJson = SharedPreferencesBenchmarkRepository.exportJson(records),
                            exportCsv = SharedPreferencesBenchmarkRepository.exportCsv(records),
                        )
                    }
                    refresh()
                }.onFailure { error ->
                    _state.update {
                        it.copy(running = false, errorMessage = error.message ?: uiStrings.get(R.string.settings_bench_failed))
                    }
                }
        }
    }

    fun compareBaselineVsParallel() {
        viewModelScope.launch {
            _state.update { it.copy(running = true, errorMessage = null) }
            runCatching { benchmarks.compareWorkers() }
                .onSuccess { comparison ->
                    _state.update {
                        it.copy(
                            running = false,
                            comparison = comparison,
                            statusLabel =
                                uiStrings.get(
                                    R.string.settings_bench_speedup_label,
                                    comparison.speedup?.let { s -> String.format("%.2fx", s) } ?: "—",
                                ),
                        )
                    }
                    refresh()
                }.onFailure { error ->
                    _state.update {
                        it.copy(running = false, errorMessage = error.message ?: uiStrings.get(R.string.settings_bench_compare_failed))
                    }
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            benchmarks.clear()
            _state.update {
                BenchmarkSettingsUiState(statusLabel = uiStrings.get(R.string.settings_bench_none))
            }
        }
    }
}

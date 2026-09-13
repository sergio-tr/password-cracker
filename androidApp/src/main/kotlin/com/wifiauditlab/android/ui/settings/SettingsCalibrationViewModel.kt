package com.wifiauditlab.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.engine.CalibrationEnvironmentProvider
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class CalibrationSettingsUiState(
    val loading: Boolean = true,
    val recalibrating: Boolean = false,
    val hasRecord: Boolean = false,
    val usable: Boolean = false,
    val statusLabel: String = "Sin calibración",
    val lastCalibrationLabel: String = "—",
    val throughputLabel: String = "—",
    val sampleDurationLabel: String = "—",
    val fingerprintLabel: String = "—",
    val errorMessage: String? = null,
)

class SettingsCalibrationViewModel(
    private val calibration: SearchCalibrationService,
    private val environmentProvider: CalibrationEnvironmentProvider,
    private val defaultStrategyId: String = LengthPrioritizedStrategy.ID.value,
    private val defaultWorkerCount: Int = 1,
) : ViewModel() {
    private val _state = MutableStateFlow(CalibrationSettingsUiState())
    val state: StateFlow<CalibrationSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val last = calibration.lastRecord()
            val usable = calibration.loadUsable(defaultStrategyId, defaultWorkerCount)
            _state.value = map(last, usable)
        }
    }

    fun recalibrate() {
        viewModelScope.launch {
            _state.update { it.copy(recalibrating = true, errorMessage = null) }
            runCatching {
                calibration.calibrate(
                    strategyId = defaultStrategyId,
                    workerCount = defaultWorkerCount,
                    force = true,
                )
            }.onSuccess {
                val last = calibration.lastRecord()
                val usable = calibration.loadUsable(defaultStrategyId, defaultWorkerCount)
                _state.value = map(last, usable).copy(recalibrating = false)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        recalibrating = false,
                        errorMessage = "No se pudo recalibrar: ${error.message ?: "error"}",
                    )
                }
            }
        }
    }

    private fun map(
        last: CalibrationRecord?,
        usable: CalibrationRecord?,
    ): CalibrationSettingsUiState {
        val env = environmentProvider.current()
        if (last == null) {
            return CalibrationSettingsUiState(
                loading = false,
                hasRecord = false,
                usable = false,
                statusLabel = "Sin calibración",
                fingerprintLabel = fingerprint(env.engineVersion, env.abi, env.appVersion),
            )
        }
        val status =
            when {
                usable != null -> "Compatible"
                !last.isCompatibleWith(
                    strategyId = defaultStrategyId,
                    engineVersion = env.engineVersion,
                    workerCount = defaultWorkerCount,
                    deviceClass = env.deviceClass,
                    abi = env.abi,
                    appVersion = env.appVersion,
                ) -> "Incompatible"
                last.isStale(System.currentTimeMillis()) -> "Obsoleta"
                else -> "Sin calibración"
            }
        return CalibrationSettingsUiState(
            loading = false,
            hasRecord = true,
            usable = usable != null,
            statusLabel = status,
            lastCalibrationLabel = formatTimestamp(last.timestampEpochMillis),
            throughputLabel = formatThroughput(last.measuredAttemptsPerSecond),
            sampleDurationLabel = "${last.sampleDurationMillis} ms",
            fingerprintLabel =
                fingerprint(last.engineVersion, last.abi, last.appVersion) +
                    " · ${last.strategyId} · ${last.workerCount} workers",
        )
    }

    private fun fingerprint(
        engineVersion: String,
        abi: String,
        appVersion: String,
    ): String = "engine $engineVersion · ABI $abi · app $appVersion"

    private fun formatTimestamp(epochMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

    private fun formatThroughput(value: Double): String =
        when {
            value >= 1_000_000 -> String.format("%.2f M intentos/s", value / 1_000_000.0)
            value >= 1_000 -> String.format("%.1f k intentos/s", value / 1_000.0)
            else -> String.format("%.0f intentos/s", value)
        }
}

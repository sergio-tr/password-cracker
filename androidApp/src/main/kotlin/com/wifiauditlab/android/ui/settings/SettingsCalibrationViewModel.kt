package com.wifiauditlab.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.android.R
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.engine.CalibrationEnvironmentProvider
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalibrationSettingsUiState(
    val loading: Boolean = true,
    val recalibrating: Boolean = false,
    val hasRecord: Boolean = false,
    val usable: Boolean = false,
    val status: CalibrationStatus = CalibrationStatus.None,
    val lastCalibrationEpochMillis: Long? = null,
    val throughput: Double? = null,
    val sampleDurationMillis: Long? = null,
    val environmentFingerprint: CalibrationFingerprint? = null,
    val recordFingerprint: CalibrationRecordFingerprint? = null,
    val errorMessage: SettingsUiMessage? = null,
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
                        errorMessage =
                            SettingsUiMessage(
                                messageRes = R.string.settings_cal_recalibrate_failed,
                                detail = error.message ?: "error",
                            ),
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
        val envFingerprint =
            CalibrationFingerprint(
                engineVersion = env.engineVersion,
                abi = env.abi,
                appVersion = env.appVersion,
            )
        if (last == null) {
            return CalibrationSettingsUiState(
                loading = false,
                hasRecord = false,
                usable = false,
                status = CalibrationStatus.None,
                environmentFingerprint = envFingerprint,
            )
        }
        val status =
            when {
                usable != null -> CalibrationStatus.Compatible
                !last.isCompatibleWith(
                    strategyId = defaultStrategyId,
                    engineVersion = env.engineVersion,
                    workerCount = defaultWorkerCount,
                    deviceClass = env.deviceClass,
                    abi = env.abi,
                    appVersion = env.appVersion,
                ) -> CalibrationStatus.Incompatible
                last.isStale(System.currentTimeMillis()) -> CalibrationStatus.Stale
                else -> CalibrationStatus.None
            }
        return CalibrationSettingsUiState(
            loading = false,
            hasRecord = true,
            usable = usable != null,
            status = status,
            lastCalibrationEpochMillis = last.timestampEpochMillis,
            throughput = last.measuredAttemptsPerSecond,
            sampleDurationMillis = last.sampleDurationMillis,
            environmentFingerprint = envFingerprint,
            recordFingerprint =
                CalibrationRecordFingerprint(
                    fingerprint =
                        CalibrationFingerprint(
                            engineVersion = last.engineVersion,
                            abi = last.abi,
                            appVersion = last.appVersion,
                        ),
                    strategyId = last.strategyId,
                    workerCount = last.workerCount,
                ),
        )
    }
}

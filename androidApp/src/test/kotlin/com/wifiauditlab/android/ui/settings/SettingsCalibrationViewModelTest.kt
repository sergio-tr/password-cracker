package com.wifiauditlab.android.ui.settings

import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.audit.UiStrings
import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.InMemoryCalibrationRepository
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.DefaultSearchCalibrationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val env =
        CalibrationEnvironment(
            engineVersion = LabEngineVersion.CURRENT,
            deviceClass = "test",
            abi = "test-abi",
            appVersion = "1.0-test",
        )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private val spanishUiStrings =
        UiStrings { id, args ->
            when (id) {
                R.string.settings_cal_none -> "Sin calibración"
                R.string.settings_cal_compatible -> "Compatible"
                R.string.settings_cal_incompatible -> "Incompatible"
                R.string.settings_cal_stale -> "Obsoleta"
                R.string.settings_cal_fingerprint_template -> "engine ${args[0]} · ABI ${args[1]} · app ${args[2]}"
                R.string.settings_cal_fingerprint_record -> "${args[0]} · ${args[1]} · ${args[2]} workers"
                R.string.settings_cal_sample_ms -> "${args[0]} ms"
                R.string.settings_cal_throughput_m -> String.format("%.2f M intentos/s", args[0] as Double)
                R.string.settings_cal_throughput_k -> String.format("%.1f k intentos/s", args[0] as Double)
                R.string.settings_cal_throughput_raw -> String.format("%.0f intentos/s", args[0] as Double)
                R.string.settings_cal_recalibrate_failed -> "No se pudo recalibrar: ${args[0]}"
                else -> error("Missing Spanish test string for resource id=$id")
            }
        }

    private fun service(repository: InMemoryCalibrationRepository = InMemoryCalibrationRepository()): SearchCalibrationService =
        DefaultSearchCalibrationService(
            repository = repository,
            environmentProvider = { env },
            nowMillis = { 1_700_000_000_000L },
            clock = { 42_000.0 },
        )

    @Test
    fun refresh_without_record_shows_empty_status() =
        runTest(dispatcher) {
            val vm = SettingsCalibrationViewModel(service(), { env }, uiStrings = spanishUiStrings)
            advanceUntilIdle()
            assertEquals("Sin calibración", vm.state.value.statusLabel)
            assertEquals(false, vm.state.value.hasRecord)
        }

    @Test
    fun recalibrate_persists_and_marks_compatible() =
        runTest(dispatcher) {
            val repository = InMemoryCalibrationRepository()
            val vm =
                SettingsCalibrationViewModel(
                    calibration = service(repository),
                    environmentProvider = { env },
                    defaultStrategyId = "length-prioritized",
                    defaultWorkerCount = 1,
                    uiStrings = spanishUiStrings,
                )
            advanceUntilIdle()
            vm.recalibrate()
            advanceUntilIdle()
            assertTrue(vm.state.value.hasRecord)
            assertEquals("Compatible", vm.state.value.statusLabel)
            assertTrue(vm.state.value.throughputLabel.contains("intentos/s"))
            val stored = repository.load()
            assertEquals(42_000.0, stored!!.measuredAttemptsPerSecond, 0.0)
        }

    @Test
    fun refresh_marks_incompatible_when_engine_changes() =
        runTest(dispatcher) {
            val repository = InMemoryCalibrationRepository()
            repository.save(
                CalibrationRecord(
                    strategyId = "length-prioritized",
                    engineVersion = "0",
                    workerCount = 1,
                    deviceClass = env.deviceClass,
                    abi = env.abi,
                    appVersion = env.appVersion,
                    measuredAttemptsPerSecond = 1_000.0,
                    sampleDurationMillis = 10,
                    timestampEpochMillis = 1_700_000_000_000L,
                ),
            )
            val vm =
                SettingsCalibrationViewModel(
                    calibration = service(repository),
                    environmentProvider = { env },
                    uiStrings = spanishUiStrings,
                )
            advanceUntilIdle()
            assertEquals("Incompatible", vm.state.value.statusLabel)
            assertEquals(false, vm.state.value.usable)
        }
}

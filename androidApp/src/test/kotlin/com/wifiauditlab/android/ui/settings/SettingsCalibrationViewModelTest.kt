package com.wifiauditlab.android.ui.settings

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
            val vm = SettingsCalibrationViewModel(service(), { env })
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
                )
            advanceUntilIdle()
            assertEquals("Incompatible", vm.state.value.statusLabel)
            assertEquals(false, vm.state.value.usable)
        }
}

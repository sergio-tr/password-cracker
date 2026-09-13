package com.wifiauditlab.android.ui.settings

import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCalibrationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val env =
        CalibrationEnvironment(
            engineVersion = "1.0",
            deviceClass = "phone",
            abi = "arm64-v8a",
            appVersion = "0.1.0",
        )

    @Test
    fun noRecordShowsNoneStatus() =
        runTest {
            val vm = SettingsCalibrationViewModel(service(), { env })
            assertFalse(vm.state.value.loading)
            assertEquals(CalibrationStatus.None, vm.state.value.status)
            assertFalse(vm.state.value.hasRecord)
        }

    @Test
    fun compatibleRecordShowsCompatibleStatus() =
        runTest {
            val record = sampleRecord()
            val vm =
                SettingsCalibrationViewModel(
                    service(last = record, usable = record),
                    { env },
                )
            assertEquals(CalibrationStatus.Compatible, vm.state.value.status)
            assertTrue(vm.state.value.usable)
            assertNotNull(vm.state.value.throughput)
        }

    @Test
    fun incompatibleRecordShowsIncompatibleStatus() =
        runTest {
            val record = sampleRecord(engineVersion = "legacy")
            val vm =
                SettingsCalibrationViewModel(
                    service(last = record, usable = null),
                    { env },
                )
            assertEquals(CalibrationStatus.Incompatible, vm.state.value.status)
            assertFalse(vm.state.value.usable)
        }

    @Test
    fun staleRecordShowsStaleStatus() =
        runTest {
            val record =
                sampleRecord(
                    timestampEpochMillis = System.currentTimeMillis() - CalibrationRecord.DEFAULT_MAX_AGE_MILLIS - 1,
                )
            val vm =
                SettingsCalibrationViewModel(
                    service(last = record, usable = null),
                    { env },
                )
            assertEquals(CalibrationStatus.Stale, vm.state.value.status)
        }

    private fun sampleRecord(
        engineVersion: String = env.engineVersion,
        timestampEpochMillis: Long = System.currentTimeMillis(),
    ): CalibrationRecord =
        CalibrationRecord(
            strategyId = LengthPrioritizedStrategy.ID.value,
            engineVersion = engineVersion,
            workerCount = 1,
            deviceClass = env.deviceClass,
            abi = env.abi,
            appVersion = env.appVersion,
            measuredAttemptsPerSecond = 42_000.0,
            sampleDurationMillis = 250,
            timestampEpochMillis = timestampEpochMillis,
        )

    private fun service(
        last: CalibrationRecord? = null,
        usable: CalibrationRecord? = null,
    ): SearchCalibrationService =
        object : SearchCalibrationService {
            override suspend fun calibrate(
                strategyId: String,
                workerCount: Int,
                force: Boolean,
            ): CalibrationRecord = last ?: sampleRecord()

            override suspend fun lastRecord(): CalibrationRecord? = last

            override suspend fun loadUsable(
                strategyId: String,
                workerCount: Int,
            ): CalibrationRecord? = usable

            override suspend fun clear() = Unit
        }
}

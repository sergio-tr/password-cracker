package com.wifiauditlab.android.instrumentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.platform.SharedPreferencesCalibrationRepository
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.LabEngineVersion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesCalibrationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: SharedPreferencesCalibrationRepository

    @Before
    fun setUp() {
        repository = SharedPreferencesCalibrationRepository(context)
        runBlocking { repository.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { repository.clear() }
    }

    @Test
    fun create_read_update_clear() =
        runBlocking {
            assertNull(repository.load())
            val first =
                CalibrationRecord(
                    strategyId = "length-prioritized",
                    engineVersion = LabEngineVersion.CURRENT,
                    workerCount = 1,
                    deviceClass = "android-test",
                    abi = "x86_64",
                    appVersion = "test",
                    measuredAttemptsPerSecond = 11_000.0,
                    sampleDurationMillis = 25,
                    timestampEpochMillis = 1_700_000_000_000L,
                )
            repository.save(first)
            assertEquals(first, repository.load())

            val updated = first.copy(measuredAttemptsPerSecond = 22_000.0, workerCount = 2)
            repository.save(updated)
            assertEquals(updated, repository.load())

            repository.clear()
            assertNull(repository.load())
        }
}

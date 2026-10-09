package com.wifiauditlab.android.instrumentation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.platform.SharedPreferencesLabSessionEvidenceLog
import com.wifiauditlab.assessment.domain.audit.LabSessionEvidenceRecord
import com.wifiauditlab.assessment.domain.audit.LabSessionTerminationReason
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionState
import com.wifiauditlab.assessment.domain.audit.VerificationMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesLabSessionEvidenceLogInstrumentedTest {
    private lateinit var context: Context
    private val prefsName = SharedPreferencesLabSessionEvidenceLog.PREFS

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun persistsSanitizedRecords_andBoundsRetention() {
        val log =
            SharedPreferencesLabSessionEvidenceLog(
                context = context,
                maxRecords = 2,
                maxAgeMs = Long.MAX_VALUE,
                clockMs = { 1_000L },
            )
        log.append(record("a"))
        log.append(record("b"))
        log.append(record("c"))

        val all = log.all()
        assertEquals(2, all.size)
        assertEquals(listOf("b", "c"), all.map { it.sessionId })

        val reloaded = SharedPreferencesLabSessionEvidenceLog(context, maxRecords = 2, maxAgeMs = Long.MAX_VALUE)
        assertEquals(listOf("b", "c"), reloaded.all().map { it.sessionId })

        val blob =
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getString("records_json", "")
                .orEmpty()
        assertFalse(blob.contains("password", ignoreCase = true))
        assertFalse(blob.contains("ValidationCredential"))
        assertFalse(blob.contains("NetworkSecret"))
        assertTrue(blob.contains("\"sessionId\""))
    }

    private fun record(sessionId: String): LabSessionEvidenceRecord =
        LabSessionEvidenceRecord(
            sessionId = sessionId,
            timestampEpochMs = 1_000L,
            event = "terminal",
            verificationMode = VerificationMode.LAB_NETWORK_VALIDATION,
            networkSsid = "LabNet",
            networkFamily = "WPA2_PERSONAL",
            capability = "Available",
            state = LabValidationSessionState.Completed,
            terminationReason = LabSessionTerminationReason.Completed,
            adapterResultCategory = "Confirmed",
            operationsConsumed = 1,
        )
}

package com.wifiauditlab.assessment.domain.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundedInMemoryLabSessionEvidenceLogTest {
    @Test
    fun prunesByMaxRecords() {
        val log =
            BoundedInMemoryLabSessionEvidenceLog(
                maxRecords = 3,
                maxAgeMs = Long.MAX_VALUE,
                clockMs = { 1_000L },
            )
        repeat(5) { i ->
            log.append(sample(sessionId = "s$i", timestamp = 1_000L + i))
        }
        assertEquals(3, log.all().size)
        assertEquals(listOf("s2", "s3", "s4"), log.all().map { it.sessionId })
    }

    @Test
    fun prunesByAge() {
        var now = 10_000L
        val log =
            BoundedInMemoryLabSessionEvidenceLog(
                maxRecords = 100,
                maxAgeMs = 1_000L,
                clockMs = { now },
            )
        log.append(sample("old", timestamp = 8_000L))
        log.append(sample("fresh", timestamp = 9_500L))
        now = 10_500L
        assertEquals(listOf("fresh"), log.all().map { it.sessionId })
    }

    @Test
    fun recordToString_neverContainsCredentialMarkers() {
        val record =
            sample("sess-1", timestamp = 1L).copy(
                detail = "adapter=Inconclusive",
            )
        val text = record.toString()
        assertTrue("sess-1" in text)
        assertTrue("password" !in text.lowercase())
        assertTrue("ValidationCredential" !in text)
        assertTrue("NetworkSecret" !in text)
    }

    private fun sample(
        sessionId: String,
        timestamp: Long,
    ): LabSessionEvidenceRecord =
        LabSessionEvidenceRecord(
            sessionId = sessionId,
            timestampEpochMs = timestamp,
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

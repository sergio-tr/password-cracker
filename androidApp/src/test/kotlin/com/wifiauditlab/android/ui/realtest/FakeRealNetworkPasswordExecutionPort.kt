package com.wifiauditlab.android.ui.realtest

import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionPort
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionRequest
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult

/**
 * Test double that records the last [execute] request without logging credentials.
 */
class FakeRealNetworkPasswordExecutionPort(
    private val result: RealNetworkPasswordExecutionResult = RealNetworkPasswordExecutionResult.Accepted,
) : RealNetworkPasswordExecutionPort {
    var executeCallCount: Int = 0
        private set

    var lastRequest: RealNetworkPasswordExecutionRequest? = null
        private set

    var cancelCallCount: Int = 0
        private set

    override fun cancel() {
        cancelCallCount++
    }

    override suspend fun execute(
        request: RealNetworkPasswordExecutionRequest,
    ): RealNetworkPasswordExecutionResult {
        executeCallCount++
        lastRequest = request
        return result
    }
}

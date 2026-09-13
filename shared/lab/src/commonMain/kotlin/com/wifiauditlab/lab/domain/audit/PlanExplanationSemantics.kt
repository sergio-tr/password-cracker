package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

enum class PlanExplanationHeadline {
    AutomaticConfiguration,
}

sealed interface PlanExplanationDetail {
    data class WorkerCount(
        val count: Int,
    ) : PlanExplanationDetail

    data class StageCount(
        val count: Int,
    ) : PlanExplanationDetail

    data class BudgetLimit(
        val maxDuration: Duration?,
        val maxAttempts: CombinationCount?,
    ) : PlanExplanationDetail

    data object DeviceAdapted : PlanExplanationDetail
}

package com.wifiauditlab.lab.domain

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Identifies a lab search strategy implementation (e.g. brute force, dictionary-shaped). */
@JvmInline
value class SearchStrategyId(val value: String) {
    init {
        require(value.isNotBlank()) { "SearchStrategyId must not be blank" }
    }

    override fun toString(): String = value
}

/** Identifies a single, running or finished, lab search session. */
@JvmInline
value class SearchSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SearchSessionId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): SearchSessionId = SearchSessionId(Uuid.random().toString())
    }
}

/** Identifies a synthetic challenge. */
@JvmInline
value class ChallengeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChallengeId must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): ChallengeId = ChallengeId(Uuid.random().toString())
    }
}

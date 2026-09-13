package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/**
 * Reproducible challenge fingerprint for resume. Stores seed + policy so the
 * synthetic secret can be regenerated without persisting plaintext.
 */
data class LabChallengeDefinition(
    val challengeId: ChallengeId,
    val alphabetSymbols: String,
    val minLength: Int,
    val maxLength: Int,
    val seed: Long,
    val syntheticLengthWeights: Map<Int, Double> = emptyMap(),
) {
    init {
        require(alphabetSymbols.isNotEmpty()) { "alphabetSymbols must not be empty" }
        require(minLength >= 1) { "minLength must be >= 1" }
        require(maxLength >= minLength) { "maxLength must be >= minLength" }
    }

    fun toChallenge(): LabChallenge =
        LabChallenge.withHiddenSecret(
            alphabet = Alphabet.of(alphabetSymbols),
            lengthPolicy = LengthPolicy(minLength, maxLength),
            seed = seed,
            id = challengeId,
            syntheticLengthWeights = syntheticLengthWeights,
        )

    companion object {
        fun from(challenge: LabChallenge): LabChallengeDefinition {
            val seed =
                requireNotNull(challenge.seed) {
                    "pause/resume requires a seeded challenge (no plaintext secret stored)"
                }
            return LabChallengeDefinition(
                challengeId = challenge.id,
                alphabetSymbols = challenge.alphabet.symbols,
                minLength = challenge.lengthPolicy.minLength,
                maxLength = challenge.lengthPolicy.maxLength,
                seed = seed,
                syntheticLengthWeights = challenge.policy.syntheticLengthWeights,
            )
        }
    }
}

/**
 * Exact resume cursor inside an ordered [LabSearchPlan].
 *
 * [nextCandidateIndexInBucket] is the next index to try in the current bucket
 * (0-based, half-open progress: indices `[0, next)` are done).
 */
data class LabSearchCursor(
    val sessionId: SearchSessionId,
    val currentBucketIndex: Int,
    val nextCandidateIndexInBucket: CombinationCount,
    val attemptCount: CombinationCount,
    val elapsedActive: Duration,
) {
    init {
        require(currentBucketIndex >= 0) { "currentBucketIndex must be >= 0" }
        require(nextCandidateIndexInBucket >= CombinationCount.ZERO) {
            "nextCandidateIndexInBucket must be non-negative"
        }
        require(attemptCount >= CombinationCount.ZERO) { "attemptCount must be non-negative" }
        require(elapsedActive >= Duration.ZERO) { "elapsedActive must be non-negative" }
    }
}

/** Serializable subset of [SearchLimits] for durable checkpoints. */
data class SearchLimitsSnapshot(
    val maxDurationMillis: Long?,
    val maxAttemptsExact: String?,
    val progressIntervalMillis: Long,
    val batchSize: Int,
) {
    fun toLimits(): SearchLimits =
        SearchLimits.of(
            maxDuration = maxDurationMillis?.let { Duration.parse("${it}ms") },
            maxAttempts = maxAttemptsExact?.let { CombinationCount.of(it) },
            progressInterval = Duration.parse("${progressIntervalMillis}ms"),
            batchSize = batchSize,
        )

    companion object {
        fun from(limits: SearchLimits): SearchLimitsSnapshot =
            SearchLimitsSnapshot(
                maxDurationMillis = limits.maxDuration?.inWholeMilliseconds,
                maxAttemptsExact = limits.maxAttempts?.toRawDecimal(),
                progressIntervalMillis = limits.progressInterval.inWholeMilliseconds,
                batchSize = limits.batchSize,
            )
    }
}

/** Exact decimal without thousand separators — safe for durable storage. */
fun CombinationCount.toRawDecimal(): String = toExactString().replace(",", "")

/**
 * Durable lab session checkpoint. Lab-only; never stores vault secrets or the
 * synthetic plaintext when a seed is available.
 */
data class LabSessionCheckpoint(
    val sessionId: SearchSessionId,
    val challenge: LabChallengeDefinition,
    val strategyId: SearchStrategyId,
    val planSchemaVersion: String,
    val searchSpaceExact: String,
    val totalBuckets: Int,
    val limits: SearchLimitsSnapshot,
    val cursor: LabSearchCursor,
    val metrics: SearchMetrics,
    val state: SearchState,
    val workerCount: Int,
    val engineVersion: String,
) {
    init {
        require(planSchemaVersion.isNotBlank()) { "planSchemaVersion must not be blank" }
        require(searchSpaceExact.isNotBlank()) { "searchSpaceExact must not be blank" }
        require(totalBuckets >= 1) { "totalBuckets must be >= 1" }
        require(workerCount >= 1) { "workerCount must be >= 1" }
        require(engineVersion.isNotBlank()) { "engineVersion must not be blank" }
        require(state == SearchState.Paused) { "checkpoint state must be Paused, was $state" }
        require(sessionId == cursor.sessionId) { "sessionId must match cursor.sessionId" }
    }

    fun matchesPlan(plan: LabSearchPlan): Boolean =
        strategyId == plan.strategyId &&
            totalBuckets == plan.totalBuckets &&
            searchSpaceExact == plan.searchSpace.toRawDecimal()

    companion object {
        const val PLAN_SCHEMA_VERSION: String = "1"
    }
}

/** Port for durable lab session checkpoints. Implementations must not store vault data. */
interface LabSessionRepository {
    suspend fun save(checkpoint: LabSessionCheckpoint)

    suspend fun load(): LabSessionCheckpoint?

    suspend fun clear()
}

/** In-process fake for unit tests. */
class InMemoryLabSessionRepository : LabSessionRepository {
    private var checkpoint: LabSessionCheckpoint? = null

    override suspend fun save(checkpoint: LabSessionCheckpoint) {
        this.checkpoint = checkpoint
    }

    override suspend fun load(): LabSessionCheckpoint? = checkpoint

    override suspend fun clear() {
        checkpoint = null
    }
}

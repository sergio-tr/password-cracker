package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.ChallengeId
import com.wifiauditlab.lab.domain.LabChallengeDefinition
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.LabSearchCursor
import com.wifiauditlab.lab.domain.LabSessionCheckpoint
import com.wifiauditlab.lab.domain.LabSessionRepository
import com.wifiauditlab.lab.domain.SearchLimitsSnapshot
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.SearchStrategyId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Durable lab-only session checkpoint store. Never touches vault secrets;
 * reconstructs the synthetic challenge from seed + policy.
 */
class SharedPreferencesLabSessionRepository(
    context: Context,
) : LabSessionRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun save(checkpoint: LabSessionCheckpoint) =
        withContext(Dispatchers.IO) {
            prefs
                .edit()
                .putBoolean(KEY_PRESENT, true)
                .putString(KEY_SESSION_ID, checkpoint.sessionId.value)
                .putString(KEY_CHALLENGE_ID, checkpoint.challenge.challengeId.value)
                .putString(KEY_ALPHABET, checkpoint.challenge.alphabetSymbols)
                .putInt(KEY_MIN_LENGTH, checkpoint.challenge.minLength)
                .putInt(KEY_MAX_LENGTH, checkpoint.challenge.maxLength)
                .putLong(KEY_SEED, checkpoint.challenge.seed)
                .putString(KEY_STRATEGY_ID, checkpoint.strategyId.value)
                .putString(KEY_PLAN_VERSION, checkpoint.planSchemaVersion)
                .putString(KEY_SEARCH_SPACE, checkpoint.searchSpaceExact)
                .putInt(KEY_TOTAL_BUCKETS, checkpoint.totalBuckets)
                .putString(KEY_MAX_DURATION, checkpoint.limits.maxDurationMillis?.toString())
                .putString(KEY_MAX_ATTEMPTS, checkpoint.limits.maxAttemptsExact)
                .putLong(KEY_PROGRESS_INTERVAL, checkpoint.limits.progressIntervalMillis)
                .putInt(KEY_BATCH_SIZE, checkpoint.limits.batchSize)
                .putInt(KEY_BUCKET_INDEX, checkpoint.cursor.currentBucketIndex)
                .putString(KEY_NEXT_INDEX, checkpoint.cursor.nextCandidateIndexInBucket.toRaw())
                .putString(KEY_ATTEMPTS, checkpoint.cursor.attemptCount.toRaw())
                .putLong(KEY_ELAPSED_NANOS, checkpoint.cursor.elapsedActive.inWholeNanoseconds)
                .putString(KEY_METRICS_ATTEMPTS, checkpoint.metrics.attempts.toRaw())
                .putLong(KEY_METRICS_ELAPSED_NANOS, checkpoint.metrics.elapsed.inWholeNanoseconds)
                .putString(KEY_METRICS_RATE, checkpoint.metrics.attemptsPerSecond.toString())
                .putInt(KEY_METRICS_BUCKET, checkpoint.metrics.currentBucketIndex)
                .putInt(KEY_METRICS_TOTAL_BUCKETS, checkpoint.metrics.totalBuckets)
                .putString(KEY_METRICS_SPACE, checkpoint.metrics.searchSpace.toRaw())
                .putString(KEY_METRICS_PERCENT, checkpoint.metrics.processedPercentage?.toString())
                .putString(
                    KEY_METRICS_REMAINING,
                    checkpoint.metrics.estimatedRemaining?.inWholeNanoseconds?.toString(),
                )
                .putString(KEY_STATE, checkpoint.state.name)
                .putInt(KEY_WORKERS, checkpoint.workerCount)
                .putString(KEY_ENGINE_VERSION, checkpoint.engineVersion)
                .commit()
            Unit
        }

    override suspend fun load(): LabSessionCheckpoint? =
        withContext(Dispatchers.IO) {
            if (!prefs.getBoolean(KEY_PRESENT, false)) return@withContext null
            runCatching {
                val sessionId = SearchSessionId(prefs.getString(KEY_SESSION_ID, null)!!)
                val attempts = CombinationCount.of(prefs.getString(KEY_ATTEMPTS, null)!!)
                val nextIndex = CombinationCount.of(prefs.getString(KEY_NEXT_INDEX, null)!!)
                val elapsed = prefs.getLong(KEY_ELAPSED_NANOS, 0L).nanoseconds
                val maxDurationRaw = prefs.getString(KEY_MAX_DURATION, null)
                val metricsPercent = prefs.getString(KEY_METRICS_PERCENT, null)?.toDoubleOrNull()
                val remainingRaw = prefs.getString(KEY_METRICS_REMAINING, null)?.toLongOrNull()
                LabSessionCheckpoint(
                    sessionId = sessionId,
                    challenge =
                        LabChallengeDefinition(
                            challengeId = ChallengeId(prefs.getString(KEY_CHALLENGE_ID, null)!!),
                            alphabetSymbols = prefs.getString(KEY_ALPHABET, null)!!,
                            minLength = prefs.getInt(KEY_MIN_LENGTH, 1),
                            maxLength = prefs.getInt(KEY_MAX_LENGTH, 1),
                            seed = prefs.getLong(KEY_SEED, 0L),
                        ),
                    strategyId = SearchStrategyId(prefs.getString(KEY_STRATEGY_ID, null)!!),
                    planSchemaVersion = prefs.getString(KEY_PLAN_VERSION, null)!!,
                    searchSpaceExact = prefs.getString(KEY_SEARCH_SPACE, null)!!,
                    totalBuckets = prefs.getInt(KEY_TOTAL_BUCKETS, 1),
                    limits =
                        SearchLimitsSnapshot(
                            maxDurationMillis = maxDurationRaw?.toLongOrNull(),
                            maxAttemptsExact = prefs.getString(KEY_MAX_ATTEMPTS, null),
                            progressIntervalMillis = prefs.getLong(KEY_PROGRESS_INTERVAL, 250L),
                            batchSize = prefs.getInt(KEY_BATCH_SIZE, 4096),
                        ),
                    cursor =
                        LabSearchCursor(
                            sessionId = sessionId,
                            currentBucketIndex = prefs.getInt(KEY_BUCKET_INDEX, 0),
                            nextCandidateIndexInBucket = nextIndex,
                            attemptCount = attempts,
                            elapsedActive = elapsed,
                        ),
                    metrics =
                        SearchMetrics(
                            attempts = CombinationCount.of(prefs.getString(KEY_METRICS_ATTEMPTS, null)!!),
                            elapsed = prefs.getLong(KEY_METRICS_ELAPSED_NANOS, 0L).nanoseconds,
                            attemptsPerSecond = prefs.getString(KEY_METRICS_RATE, "0")!!.toDouble(),
                            currentBucketIndex = prefs.getInt(KEY_METRICS_BUCKET, 0),
                            totalBuckets = prefs.getInt(KEY_METRICS_TOTAL_BUCKETS, 1),
                            searchSpace = CombinationCount.of(prefs.getString(KEY_METRICS_SPACE, null)!!),
                            processedPercentage = metricsPercent,
                            estimatedRemaining = remainingRaw?.nanoseconds,
                        ),
                    state = SearchState.valueOf(prefs.getString(KEY_STATE, SearchState.Paused.name)!!),
                    workerCount = prefs.getInt(KEY_WORKERS, 1),
                    engineVersion = prefs.getString(KEY_ENGINE_VERSION, LabEngineVersion.CURRENT)!!,
                )
            }.getOrNull()
        }

    override suspend fun clear() =
        withContext(Dispatchers.IO) {
            prefs.edit().clear().commit()
            Unit
        }

    private fun CombinationCount.toRaw(): String = toExactString().replace(",", "")

    companion object {
        private const val PREFS = "lab_session_checkpoint"
        private const val KEY_PRESENT = "present"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_CHALLENGE_ID = "challenge_id"
        private const val KEY_ALPHABET = "alphabet"
        private const val KEY_MIN_LENGTH = "min_length"
        private const val KEY_MAX_LENGTH = "max_length"
        private const val KEY_SEED = "seed"
        private const val KEY_STRATEGY_ID = "strategy_id"
        private const val KEY_PLAN_VERSION = "plan_version"
        private const val KEY_SEARCH_SPACE = "search_space"
        private const val KEY_TOTAL_BUCKETS = "total_buckets"
        private const val KEY_MAX_DURATION = "max_duration_ms"
        private const val KEY_MAX_ATTEMPTS = "max_attempts"
        private const val KEY_PROGRESS_INTERVAL = "progress_interval_ms"
        private const val KEY_BATCH_SIZE = "batch_size"
        private const val KEY_BUCKET_INDEX = "bucket_index"
        private const val KEY_NEXT_INDEX = "next_index"
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_ELAPSED_NANOS = "elapsed_nanos"
        private const val KEY_METRICS_ATTEMPTS = "metrics_attempts"
        private const val KEY_METRICS_ELAPSED_NANOS = "metrics_elapsed_nanos"
        private const val KEY_METRICS_RATE = "metrics_rate"
        private const val KEY_METRICS_BUCKET = "metrics_bucket"
        private const val KEY_METRICS_TOTAL_BUCKETS = "metrics_total_buckets"
        private const val KEY_METRICS_SPACE = "metrics_space"
        private const val KEY_METRICS_PERCENT = "metrics_percent"
        private const val KEY_METRICS_REMAINING = "metrics_remaining_nanos"
        private const val KEY_STATE = "state"
        private const val KEY_WORKERS = "workers"
        private const val KEY_ENGINE_VERSION = "engine_version"
    }
}

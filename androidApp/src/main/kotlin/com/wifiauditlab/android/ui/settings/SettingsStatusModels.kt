package com.wifiauditlab.android.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wifiauditlab.android.R

enum class CalibrationStatus {
    None,
    Compatible,
    Incompatible,
    Stale,
}

sealed interface BenchmarkStatus {
    data object None : BenchmarkStatus

    data class RunCount(
        val count: Int,
    ) : BenchmarkStatus

    data class SuiteCompleted(
        val count: Int,
    ) : BenchmarkStatus

    data class SpeedupResult(
        val factorLabel: String,
    ) : BenchmarkStatus
}

@Composable
fun CalibrationStatus.label(): String =
    when (this) {
        CalibrationStatus.None -> stringResource(R.string.settings_cal_none)
        CalibrationStatus.Compatible -> stringResource(R.string.settings_cal_compatible)
        CalibrationStatus.Incompatible -> stringResource(R.string.settings_cal_incompatible)
        CalibrationStatus.Stale -> stringResource(R.string.settings_cal_stale)
    }

@Composable
fun BenchmarkStatus.label(): String =
    when (this) {
        BenchmarkStatus.None -> stringResource(R.string.settings_bench_none)
        is BenchmarkStatus.RunCount -> stringResource(R.string.settings_bench_runs, count)
        is BenchmarkStatus.SuiteCompleted -> stringResource(R.string.settings_bench_suite, count)
        is BenchmarkStatus.SpeedupResult -> stringResource(R.string.settings_bench_speedup_label, factorLabel)
    }

data class CalibrationFingerprint(
    val engineVersion: String,
    val abi: String,
    val appVersion: String,
)

data class CalibrationRecordFingerprint(
    val fingerprint: CalibrationFingerprint,
    val strategyId: String,
    val workerCount: Int,
)

@Composable
fun CalibrationFingerprint.label(): String =
    stringResource(
        R.string.settings_cal_fingerprint_template,
        engineVersion,
        abi,
        appVersion,
    )

@Composable
fun CalibrationRecordFingerprint.label(): String =
    stringResource(
        R.string.settings_cal_fingerprint_record,
        fingerprint.label(),
        strategyId,
        workerCount,
    )

@Composable
fun formatCalibrationThroughput(value: Double?): String =
    when {
        value == null -> "—"
        value >= 1_000_000 -> stringResource(R.string.settings_cal_throughput_m, value / 1_000_000.0)
        value >= 1_000 -> stringResource(R.string.settings_cal_throughput_k, value / 1_000.0)
        else -> stringResource(R.string.settings_cal_throughput_raw, value)
    }

@Composable
fun formatCalibrationSampleDuration(sampleDurationMillis: Long): String =
    stringResource(R.string.settings_cal_sample_ms, sampleDurationMillis)

data class SettingsUiMessage(
    @StringRes val messageRes: Int,
    val detail: String? = null,
)

@Composable
fun SettingsUiMessage.text(): String =
    detail?.let { stringResource(messageRes, it) } ?: stringResource(messageRes)

fun formatCalibrationTimestamp(epochMillis: Long?): String =
    epochMillis?.let {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(it))
    } ?: "—"

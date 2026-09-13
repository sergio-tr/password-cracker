package com.wifiauditlab.android.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.Severity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAnalysisScreen(
    viewModel: SecurityAnalysisViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backLabel = stringResource(R.string.navigate_back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_analysis_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.missingTarget -> Text(stringResource(R.string.security_missing_target))
                state.loading -> Text(stringResource(R.string.security_analyzing))
                else -> {
                    Text(state.displayName, fontWeight = FontWeight.Bold)
                    if (state.ssidLabel.isNotBlank() && state.ssidLabel != state.displayName) {
                        Text(state.ssidLabel)
                    }
                    state.assessment?.let { assessment ->
                        SummarySection(assessment)
                        WhatThisMeansSection(assessment)
                    }
                    state.authentication?.let { AuthenticationSection(it) }
                    state.assessment?.let { FindingsSection(it) }
                    RecommendationsSection(state.recommendations)
                    state.assessment?.let { assessment ->
                        TechnicalDetailsSection(
                            assessment = assessment,
                            expanded = state.technicalExpanded,
                            onToggle = viewModel::toggleTechnicalDetails,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySection(assessment: SecurityAssessment) {
    val ratingContentDescription = stringResource(R.string.security_cd_rating)
    AnalysisCard(title = stringResource(R.string.security_summary)) {
        Text(
            stringResource(ratingLabelRes(assessment.rating)),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = ratingContentDescription },
        )
        Text(assessment.headline, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WhatThisMeansSection(assessment: SecurityAssessment) {
    AnalysisCard(title = stringResource(R.string.security_what_means)) {
        Text(assessment.plainExplanation)
    }
}

@Composable
private fun AuthenticationSection(auth: AuthenticationSummary) {
    val encryption =
        auth.encryptionDynamic
            ?: auth.encryptionLabelRes?.let { stringResource(it) }
                .orEmpty()
    AnalysisCard(title = stringResource(R.string.security_authentication)) {
        Text(stringResource(R.string.security_family, stringResource(auth.familyLabelRes)))
        Text(stringResource(auth.modeLabelRes))
        Text(stringResource(R.string.security_encryption, encryption))
        auth.pmfLabelRes?.let { Text(stringResource(it)) }
        auth.transitionLabelRes?.let { Text(stringResource(it)) }
    }
}

@Composable
private fun FindingsSection(assessment: SecurityAssessment) {
    AnalysisCard(title = stringResource(R.string.security_findings)) {
        if (assessment.findings.isEmpty()) {
            Text(stringResource(R.string.security_no_findings))
        } else {
            assessment.findings.forEach { finding ->
                val findingContentDescription = stringResource(R.string.security_cd_finding, finding.title)
                Text(
                    stringResource(R.string.security_finding, severityLabel(finding.severity), finding.title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = findingContentDescription },
                )
                Text(finding.explanation)
            }
        }
    }
}

@Composable
private fun RecommendationsSection(recommendations: List<Int>) {
    AnalysisCard(title = stringResource(R.string.security_recommendations)) {
        if (recommendations.isEmpty()) {
            Text(stringResource(R.string.security_no_recommendations))
        } else {
            recommendations.forEach { tipRes -> Text("• ${stringResource(tipRes)}") }
        }
    }
}

@Composable
private fun TechnicalDetailsSection(
    assessment: SecurityAssessment,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    AnalysisCard(title = stringResource(R.string.security_technical)) {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (expanded) {
                    stringResource(R.string.security_technical_hide)
                } else {
                    stringResource(R.string.security_technical_show)
                },
            )
        }
        if (expanded) {
            Text(assessment.technicalSummary)
        }
    }
}

@Composable
private fun AnalysisCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun severityLabel(severity: Severity): String =
    when (severity) {
        Severity.INFO -> stringResource(R.string.security_severity_info)
        Severity.LOW -> stringResource(R.string.security_severity_low)
        Severity.MEDIUM -> stringResource(R.string.security_severity_medium)
        Severity.HIGH -> stringResource(R.string.security_severity_high)
        Severity.CRITICAL -> stringResource(R.string.security_severity_critical)
    }

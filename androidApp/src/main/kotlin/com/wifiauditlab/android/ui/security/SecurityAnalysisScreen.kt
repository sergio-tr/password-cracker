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
                state.missingTarget -> Text("No hay una red seleccionada para analizar.")
                state.loading -> Text("Analizando seguridad…")
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
    AnalysisCard(title = "Resumen") {
        Text(
            ratingLabel(assessment.rating),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = "Security rating" },
        )
        Text(assessment.headline, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WhatThisMeansSection(assessment: SecurityAssessment) {
    AnalysisCard(title = "Qué significa") {
        Text(assessment.plainExplanation)
    }
}

@Composable
private fun AuthenticationSection(auth: AuthenticationSummary) {
    AnalysisCard(title = "Autenticación") {
        Text("Familia: ${auth.familyLabel}")
        Text(auth.modeLabel)
        Text("Cifrado / key mgmt: ${auth.encryptionLabel}")
        auth.pmfLabel?.let { Text(it) }
        auth.transitionLabel?.let { Text(it) }
    }
}

@Composable
private fun FindingsSection(assessment: SecurityAssessment) {
    AnalysisCard(title = "Hallazgos") {
        if (assessment.findings.isEmpty()) {
            Text("No hay hallazgos adicionales.")
        } else {
            assessment.findings.forEach { finding ->
                Text(
                    "${severityLabel(finding.severity)} · ${finding.title}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = "Finding ${finding.title}" },
                )
                Text(finding.explanation)
            }
        }
    }
}

@Composable
private fun RecommendationsSection(recommendations: List<String>) {
    AnalysisCard(title = "Recomendaciones") {
        if (recommendations.isEmpty()) {
            Text("Sin recomendaciones adicionales.")
        } else {
            recommendations.forEach { tip -> Text("• $tip") }
        }
    }
}

@Composable
private fun TechnicalDetailsSection(
    assessment: SecurityAssessment,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    AnalysisCard(title = "Detalles técnicos") {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Ocultar detalles técnicos" else "Mostrar detalles técnicos")
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

private fun severityLabel(severity: Severity): String =
    when (severity) {
        Severity.INFO -> "Info"
        Severity.LOW -> "Baja"
        Severity.MEDIUM -> "Media"
        Severity.HIGH -> "Alta"
        Severity.CRITICAL -> "Crítica"
    }

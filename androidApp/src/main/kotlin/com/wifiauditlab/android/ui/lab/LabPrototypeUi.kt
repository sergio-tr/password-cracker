package com.wifiauditlab.android.ui.lab

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.security.ratingLabelRes
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

@Composable
fun PrototypePresetPicker(
    prototype: LocalNetworkPrototype,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
) {
    val selectedPreset = PrototypeSecurityPreset.matching(prototype.securityProfile)
    var showEnterpriseAlt by remember {
        mutableStateOf(selectedPreset in PrototypeSecurityPreset.ENTERPRISE_ALTERNATIVES)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        PrototypeSecurityPreset.PRIMARY.forEach { preset ->
            FilterChip(
                selected = selectedPreset == preset,
                onClick = {
                    showEnterpriseAlt = preset in PrototypeSecurityPreset.ENTERPRISE_ALTERNATIVES
                    onPrototypeChange(prototype.withPreset(preset))
                },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
    }
    if (showEnterpriseAlt || selectedPreset in PrototypeSecurityPreset.ENTERPRISE_ALTERNATIVES) {
        Text(stringResource(R.string.lab_prototype_security), fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PrototypeSecurityPreset.ENTERPRISE_ALTERNATIVES.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onPrototypeChange(prototype.withPreset(preset)) },
                    label = { Text(stringResource(preset.labelRes)) },
                )
            }
        }
    }
}

@Composable
fun PrototypeAdvancedOptions(
    prototype: LocalNetworkPrototype,
    onPrototypeChange: (LocalNetworkPrototype) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lab_prototype_standard), fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = prototype.standard == null,
                onClick = { onPrototypeChange(prototype.copy(standard = null)) },
                label = { Text(stringResource(R.string.lab_prototype_optional_none)) },
            )
            LAB_OPTIONAL_WIFI_STANDARDS.forEach { standard ->
                FilterChip(
                    selected = prototype.standard == standard,
                    onClick = { onPrototypeChange(prototype.copy(standard = standard)) },
                    label = { Text(standardLabel(standard)) },
                )
            }
        }
        Text(stringResource(R.string.lab_prototype_band), fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = prototype.band == null,
                onClick = { onPrototypeChange(prototype.copy(band = null)) },
                label = { Text(stringResource(R.string.lab_prototype_optional_none)) },
            )
            LAB_OPTIONAL_WIFI_BANDS.forEach { band ->
                FilterChip(
                    selected = prototype.band == band,
                    onClick = { onPrototypeChange(prototype.copy(band = band)) },
                    label = { Text(stringResource(bandDisplayLabelRes(band))) },
                )
            }
        }
        Text(stringResource(R.string.lab_prototype_pmf), fontWeight = FontWeight.Medium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            LAB_PMF_OPTIONS.forEach { pmf ->
                FilterChip(
                    selected = prototype.securityProfile.managementFrameProtection == pmf,
                    onClick = { onPrototypeChange(prototype.withPmf(pmf)) },
                    label = { Text(stringResource(pmfLabelRes(pmf))) },
                )
            }
        }
    }
}

@Composable
fun PrototypeAssessmentCard(
    assessment: SecurityAssessment?,
    loading: Boolean,
    prototype: LocalNetworkPrototype,
) {
    val heading = stringResource(R.string.lab_prototype_assessment_heading)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(heading, fontWeight = FontWeight.SemiBold)
        when {
            loading -> Text(stringResource(R.string.lab_prototype_assessment_loading))
            assessment != null -> {
                val ratingCd = stringResource(R.string.security_cd_rating)
                Text(
                    stringResource(ratingLabelRes(assessment.rating)),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = ratingCd },
                )
                Text(assessment.headline, fontWeight = FontWeight.Medium)
                Text(assessment.plainExplanation)
            }
            else -> Text(stringResource(R.string.lab_prototype_assessment_loading))
        }
        if (!prototype.securityFamily.supportsSharedPasswordDemo()) {
            Text(
                stringResource(R.string.lab_prototype_no_password_audit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun pmfLabelRes(pmf: ManagementFrameProtection): Int =
    when (pmf) {
        ManagementFrameProtection.REQUIRED -> R.string.security_pmf_required
        ManagementFrameProtection.CAPABLE -> R.string.security_pmf_capable
        ManagementFrameProtection.DISABLED -> R.string.security_pmf_disabled
        ManagementFrameProtection.UNKNOWN -> R.string.lab_prototype_optional_none
    }

private val LAB_OPTIONAL_WIFI_STANDARDS =
    listOf(
        WifiStandard.WIFI_4,
        WifiStandard.WIFI_5,
        WifiStandard.WIFI_6,
        WifiStandard.WIFI_6E,
        WifiStandard.WIFI_7,
    )

private val LAB_OPTIONAL_WIFI_BANDS =
    listOf(
        WifiBand.GHZ_2_4,
        WifiBand.GHZ_5,
        WifiBand.GHZ_6,
    )

private val LAB_PMF_OPTIONS =
    listOf(
        ManagementFrameProtection.UNKNOWN,
        ManagementFrameProtection.DISABLED,
        ManagementFrameProtection.CAPABLE,
        ManagementFrameProtection.REQUIRED,
    )

package com.wifiauditlab.android.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiauditlab.android.R
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.audit.CandidateModel
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.audit.PasswordAuditStage
import com.wifiauditlab.lab.domain.audit.PlanExplanationDetail
import com.wifiauditlab.lab.domain.audit.SharedPasswordSearchProfile

data class SearchStageUiSummary(
    val alphabet: Alphabet,
    val lengthPolicy: LengthPolicy,
    val weightPercent: Int,
)

data class SearchPlanUiSummary(
    val profile: SharedPasswordSearchProfile,
    val stages: List<SearchStageUiSummary>,
)

fun PasswordAuditPlan.toSearchPlanUiSummary(): SearchPlanUiSummary? {
    val profile =
        explanation.details.filterIsInstance<PlanExplanationDetail.WifiPskMechanism>().firstOrNull()?.profile
            ?: return null
    return SearchPlanUiSummary(
        profile = profile,
        stages =
            stages.map { stage ->
                SearchStageUiSummary(
                    alphabet = stage.candidateModel.alphabet,
                    lengthPolicy = stage.candidateModel.lengthPolicy,
                    weightPercent = stage.budget.weight,
                )
            },
    )
}

@StringRes
fun SharedPasswordSearchProfile.labelRes(): Int =
    when (this) {
        SharedPasswordSearchProfile.WPA2_PERSONAL_PSK -> R.string.search_profile_wpa2_personal_psk
        SharedPasswordSearchProfile.WPA3_PERSONAL_PSK -> R.string.search_profile_wpa3_personal_psk
        SharedPasswordSearchProfile.WPA2_WPA3_TRANSITION_PSK -> R.string.search_profile_wpa2_wpa3_transition_psk
        SharedPasswordSearchProfile.WPA_PERSONAL_PSK -> R.string.search_profile_wpa_personal_psk
    }

@Composable
fun SharedPasswordSearchProfile.label(): String = stringResource(labelRes())

@Composable
fun SearchPlanExplainabilitySection(
    summary: SearchPlanUiSummary?,
    stagesExpanded: Boolean,
    onToggleStagesExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summary == null) return
    val howSearchCd = stringResource(R.string.search_plan_cd_how_search)
    Column(
        modifier
            .fillMaxWidth()
            .testTag("search_plan_explainability"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.search_plan_profile_label, summary.profile.label()),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("search_plan_profile"),
        )
        Text(
            stringResource(R.string.search_plan_psk_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onToggleStagesExpanded,
            modifier =
                Modifier.semantics {
                    contentDescription = howSearchCd
                },
        ) {
            Text(
                if (stagesExpanded) {
                    stringResource(R.string.search_plan_hide_stages)
                } else {
                    stringResource(R.string.search_plan_how_search)
                },
            )
        }
        if (stagesExpanded) {
            summary.stages.forEach { stage ->
                Text(
                    stringResource(
                        R.string.search_plan_stage_weight,
                        stage.readableStageLabel(),
                        stage.weightPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("search_plan_stage"),
                )
            }
        }
    }
}

@Composable
private fun SearchStageUiSummary.readableStageLabel(): String =
    CandidateModel(alphabet, lengthPolicy).readableStageLabel()

@Composable
fun CandidateModel.readableStageLabel(): String {
    val exact8 = lengthPolicy.minLength == 8 && lengthPolicy.maxLength == 8
    val range8to10 = lengthPolicy.minLength == 8 && lengthPolicy.maxLength == 10
    val range8to12 = lengthPolicy.minLength == 8 && lengthPolicy.maxLength == 12
    return when {
        alphabet == Alphabet.DIGITS && exact8 -> stringResource(R.string.search_plan_stage_digits_8)
        alphabet == Alphabet.LOWERCASE && exact8 -> stringResource(R.string.search_plan_stage_lower_8)
        alphabet == Alphabet.LOWER_ALPHANUMERIC && exact8 ->
            stringResource(R.string.search_plan_stage_lower_alnum_8)
        alphabet == Alphabet.LOWER_ALPHANUMERIC && range8to10 ->
            stringResource(R.string.search_plan_stage_lower_alnum_8_10)
        alphabet == Alphabet.ALPHANUMERIC && range8to12 ->
            stringResource(R.string.search_plan_stage_alnum_8_12)
        alphabet == Alphabet.PRINTABLE_ASCII && range8to12 ->
            stringResource(R.string.search_plan_stage_printable_8_12)
        else ->
            stringResource(
                R.string.search_plan_stage_fallback,
                lengthPolicy.minLength,
                lengthPolicy.maxLength,
            )
    }
}

@Composable
fun PasswordAuditStage.readableStageLabel(): String = candidateModel.readableStageLabel()

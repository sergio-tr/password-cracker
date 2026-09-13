package com.wifiauditlab.android.ui.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wifiauditlab.android.R
import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason

sealed interface PasswordAuditUiError {
    data object MissingPassword : PasswordAuditUiError

    data object PasswordRequired : PasswordAuditUiError

    data object NoValidPlan : PasswordAuditUiError

    data object InvalidConfig : PasswordAuditUiError

    data object NotConnected : PasswordAuditUiError

    data object ConnectionLost : PasswordAuditUiError

    data object NoLongerEligible : PasswordAuditUiError

    data object PasswordUnavailable : PasswordAuditUiError

    data object VaultSaveFailed : PasswordAuditUiError

    data object AuditFailed : PasswordAuditUiError

    data object BudgetRequired : PasswordAuditUiError

    data object NoPlan : PasswordAuditUiError

    data class PlanNotApplicable(
        val reason: PasswordAuditInapplicableReason,
    ) : PasswordAuditUiError
}

@Composable
fun PasswordAuditUiError.message(): String =
    when (this) {
        PasswordAuditUiError.MissingPassword -> stringResource(R.string.audit_err_missing_password)
        PasswordAuditUiError.PasswordRequired -> stringResource(R.string.audit_err_password_required)
        PasswordAuditUiError.NoValidPlan -> stringResource(R.string.audit_err_no_valid_plan)
        PasswordAuditUiError.InvalidConfig -> stringResource(R.string.audit_err_invalid_config)
        PasswordAuditUiError.NotConnected -> stringResource(R.string.audit_err_not_connected)
        PasswordAuditUiError.ConnectionLost -> stringResource(R.string.audit_err_connection_lost)
        PasswordAuditUiError.NoLongerEligible -> stringResource(R.string.audit_err_no_longer_eligible)
        PasswordAuditUiError.PasswordUnavailable -> stringResource(R.string.audit_err_password_unavailable)
        PasswordAuditUiError.VaultSaveFailed -> stringResource(R.string.audit_err_vault_save_failed)
        PasswordAuditUiError.AuditFailed -> stringResource(R.string.audit_err_audit_failed)
        PasswordAuditUiError.BudgetRequired -> stringResource(R.string.audit_err_budget_required)
        PasswordAuditUiError.NoPlan -> stringResource(R.string.audit_err_no_plan)
        is PasswordAuditUiError.PlanNotApplicable -> stringResource(this.reason.labelRes())
    }

@Composable
fun PasswordAuditInapplicableReason.label(): String = stringResource(labelRes())

fun PasswordAuditInapplicableReason.labelRes(): Int =
    when (this) {
        PasswordAuditInapplicableReason.OpenNetwork -> R.string.audit_inapplicable_open
        PasswordAuditInapplicableReason.Owe -> R.string.audit_inapplicable_owe
        PasswordAuditInapplicableReason.Enterprise -> R.string.audit_inapplicable_enterprise
        PasswordAuditInapplicableReason.Passpoint -> R.string.audit_inapplicable_passpoint
        PasswordAuditInapplicableReason.Dpp -> R.string.audit_inapplicable_dpp
        PasswordAuditInapplicableReason.Wep -> R.string.audit_inapplicable_wep
        PasswordAuditInapplicableReason.UnknownFamily -> R.string.audit_inapplicable_unknown
        PasswordAuditInapplicableReason.UnsupportedAuth -> R.string.audit_inapplicable_unsupported
    }

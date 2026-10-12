package com.wifiauditlab.android.ui.realtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.security.familyLabelRes
import com.wifiauditlab.assessment.domain.real.RealNetworkFailureReason
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult
import com.wifiauditlab.assessment.domain.real.RealNetworkUnavailableReason
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealNetworkPasswordTestScreen(
    onBack: () -> Unit,
    viewModel: RealNetworkPasswordTestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = stringResource(R.string.real_test_title)
    val stopCd = stringResource(R.string.real_test_stop)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.isBusy) {
                Button(
                    onClick = viewModel::stop,
                    enabled = state.phase !is RealNetworkPasswordTestPhase.Stopping,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .semantics { contentDescription = stopCd },
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.phase is RealNetworkPasswordTestPhase.Stopping) {
                            stringResource(R.string.real_test_stopping)
                        } else {
                            stringResource(R.string.real_test_stop)
                        },
                    )
                }
            }
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
            if (state.missingTarget) {
                Text(stringResource(R.string.real_test_missing_target))
                return@Column
            }
            val network = state.network
            if (network != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(network.displayName, fontWeight = FontWeight.Bold)
                        Text(stringResource(familyLabelRes(network.securityFamily)))
                        when {
                            state.networkEligible && state.bssidConfirmed ->
                                Text(
                                    stringResource(R.string.real_test_connected_exact),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            state.networkEligible ->
                                Text(
                                    stringResource(R.string.real_test_connected_ssid),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            else ->
                                Text(stringResource(R.string.real_test_not_connected))
                        }
                    }
                }
            }

            if (state.mayUseVaultSecret) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.secretSource == RealNetworkPasswordSecretSource.Manual,
                        onClick = {
                            viewModel.selectSecretSource(RealNetworkPasswordSecretSource.Manual)
                        },
                        label = { Text(stringResource(R.string.real_test_source_manual)) },
                    )
                    FilterChip(
                        selected = state.secretSource == RealNetworkPasswordSecretSource.Vault,
                        onClick = {
                            viewModel.selectSecretSource(RealNetworkPasswordSecretSource.Vault)
                        },
                        label = { Text(stringResource(R.string.real_test_source_vault)) },
                    )
                }
                if (state.secretSource == RealNetworkPasswordSecretSource.Vault &&
                    !state.vaultSecretAuthorized
                ) {
                    Text(stringResource(R.string.real_test_vault_auth_body))
                    OutlinedButton(
                        onClick = viewModel::authorizeAndLoadVaultSecret,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.real_test_vault_auth_cta))
                    }
                }
            }

            Text(stringResource(R.string.real_test_password_label), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = viewModel::onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
                singleLine = true,
                visualTransformation =
                    if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisible) {
                        Icon(
                            if (state.passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription =
                                if (state.passwordVisible) {
                                    stringResource(R.string.real_test_hide_password)
                                } else {
                                    stringResource(R.string.real_test_show_password)
                                },
                        )
                    }
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.consentChecked,
                    onCheckedChange = viewModel::setConsentChecked,
                    enabled = !state.isBusy,
                )
                Text(
                    stringResource(R.string.real_test_consent),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (!state.isBusy) {
                Button(
                    onClick = viewModel::startRealNetworkTest,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.real_test_start))
                }
            }

            when (val phase = state.phase) {
                RealNetworkPasswordTestPhase.Running ->
                    Text(stringResource(R.string.real_test_running_body))
                RealNetworkPasswordTestPhase.Stopping ->
                    Text(stringResource(R.string.real_test_stopping))
                is RealNetworkPasswordTestPhase.Completed ->
                    ResultCard(phase.result)
                is RealNetworkPasswordTestPhase.Error ->
                    Text(phase.messageResHint)
                RealNetworkPasswordTestPhase.Ready -> Unit
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResultCard(result: RealNetworkPasswordExecutionResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.real_test_result_heading), fontWeight = FontWeight.Bold)
            Text(
                when (result) {
                    RealNetworkPasswordExecutionResult.Accepted ->
                        stringResource(R.string.real_test_result_accepted)
                    RealNetworkPasswordExecutionResult.Rejected ->
                        stringResource(R.string.real_test_result_rejected)
                    RealNetworkPasswordExecutionResult.Inconclusive ->
                        stringResource(R.string.real_test_result_inconclusive)
                    RealNetworkPasswordExecutionResult.Cancelled ->
                        stringResource(R.string.real_test_result_cancelled)
                    is RealNetworkPasswordExecutionResult.Unavailable ->
                        unavailableLabel(result.reason)
                    is RealNetworkPasswordExecutionResult.Failed ->
                        failedLabel(result.reason)
                },
            )
        }
    }
}

@Composable
private fun unavailableLabel(reason: RealNetworkUnavailableReason): String =
    when (reason) {
        RealNetworkUnavailableReason.NotImplemented ->
            stringResource(R.string.real_test_result_not_implemented)
        RealNetworkUnavailableReason.MissingPermissions ->
            stringResource(R.string.real_test_result_missing_permissions)
        RealNetworkUnavailableReason.CapabilityUnavailable ->
            stringResource(R.string.real_test_result_capability)
        RealNetworkUnavailableReason.NetworkNotCurrent ->
            stringResource(R.string.real_test_result_not_current)
        RealNetworkUnavailableReason.ConsentRequired ->
            stringResource(R.string.real_test_result_consent)
        RealNetworkUnavailableReason.InsufficientInformation ->
            stringResource(R.string.real_test_result_insufficient_info)
    }

@Composable
private fun failedLabel(reason: RealNetworkFailureReason): String =
    when (reason) {
        RealNetworkFailureReason.PlatformError ->
            stringResource(R.string.real_test_result_platform_error)
        RealNetworkFailureReason.Timeout ->
            stringResource(R.string.real_test_result_timeout)
        RealNetworkFailureReason.Unknown ->
            stringResource(R.string.real_test_result_unknown)
    }

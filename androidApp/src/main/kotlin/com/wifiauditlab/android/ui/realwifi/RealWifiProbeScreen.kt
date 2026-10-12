package com.wifiauditlab.android.ui.realwifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestDenial
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealWifiProbeScreen(
    onBack: () -> Unit,
    viewModel: RealWifiProbeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose { }
    }

    if (state.showConsentDialog) {
        ConsentDialog(
            checked = state.consentChecked,
            onCheckedChange = viewModel::setConsentChecked,
            onDismiss = viewModel::dismissConsentDialog,
            onConfirm = viewModel::confirmConsentAndStart,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.real_wifi_probe_title)) },
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
            if (state.isActive) {
                val stopCd = stringResource(R.string.real_wifi_probe_stop)
                Button(
                    onClick = viewModel::stop,
                    enabled = state.phase != RealWifiProbePhase.Stopping,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .semantics { contentDescription = stopCd },
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        if (state.phase == RealWifiProbePhase.Stopping) {
                            stringResource(R.string.real_wifi_probe_stopping)
                        } else {
                            stringResource(R.string.real_wifi_probe_stop)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (!state.featureCompiledIn) {
                UnavailableCard(stringResource(R.string.real_wifi_probe_unavailable_flavor))
                return@Column
            }
            if (state.fixedLab == null) {
                UnavailableCard(stringResource(R.string.real_wifi_probe_unavailable_config))
                return@Column
            }

            LabNetworkCard(state)
            ChecklistCard(state)

            if (state.phase == RealWifiProbePhase.Running || state.phase == RealWifiProbePhase.Stopping) {
                RunningCard(state)
            }

            if (!state.isActive) {
                PasswordCard(
                    state = state,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onToggleVisibility = viewModel::togglePasswordVisibility,
                    onStart = viewModel::requestStart,
                )
            }

            when (state.phase) {
                RealWifiProbePhase.Denied -> DenialsCard(state.denials)
                RealWifiProbePhase.Completed,
                RealWifiProbePhase.Failed,
                -> state.outcome?.let { OutcomeCard(it) }
                RealWifiProbePhase.Cancelled -> {
                    Text(
                        stringResource(R.string.real_wifi_probe_cancelled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> Unit
            }

            LearningSection(
                expanded = state.learningExpanded,
                onToggle = viewModel::toggleLearning,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConsentDialog(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.real_wifi_probe_consent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.real_wifi_probe_consent_body))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                    Text(stringResource(R.string.real_wifi_probe_consent_checkbox))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = checked) {
                Text(stringResource(R.string.real_wifi_probe_consent_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.real_wifi_probe_consent_cancel))
            }
        },
    )
}

@Composable
private fun UnavailableCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.real_wifi_probe_unavailable_title), fontWeight = FontWeight.Bold)
            Text(message)
        }
    }
}

@Composable
private fun LabNetworkCard(state: RealWifiProbeUiState) {
    val fixed = state.fixedLab ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.real_wifi_probe_network_heading), fontWeight = FontWeight.Bold)
            Text(fixed.ssidNormalized, fontWeight = FontWeight.SemiBold)
            Text(fixed.bssidNormalized.value.uppercase())
        }
    }
}

@Composable
private fun ChecklistCard(state: RealWifiProbeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckRow(
                ok = state.fixedLab != null,
                okText = stringResource(R.string.real_wifi_probe_check_authorized),
                failText = stringResource(R.string.real_wifi_probe_check_authorized_fail),
            )
            CheckRow(
                ok = state.connectedToFixedLab,
                okText = stringResource(R.string.real_wifi_probe_check_connected),
                failText = stringResource(R.string.real_wifi_probe_check_connected_fail),
            )
            CheckRow(
                ok = state.labModeEnabled,
                okText = stringResource(R.string.real_wifi_probe_check_lab_mode),
                failText = stringResource(R.string.real_wifi_probe_check_lab_mode_fail),
            )
        }
    }
}

@Composable
private fun CheckRow(
    ok: Boolean,
    okText: String,
    failText: String,
) {
    Text(if (ok) "✓ $okText" else "✗ $failText")
}

@Composable
private fun RunningCard(state: RealWifiProbeUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.real_wifi_probe_running_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.real_wifi_probe_running_body))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PasswordCard(
    state: RealWifiProbeUiState,
    onPasswordChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onStart: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.real_wifi_probe_password_label), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.real_wifi_probe_password_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation =
                    if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription =
                                stringResource(
                                    if (state.passwordVisible) {
                                        R.string.real_wifi_probe_hide_password
                                    } else {
                                        R.string.real_wifi_probe_show_password
                                    },
                                ),
                        )
                    }
                },
                isError = state.passwordError != null,
                supportingText = {
                    state.passwordError?.let {
                        Text(
                            when (it) {
                                "psk_too_short" -> stringResource(R.string.real_wifi_probe_psk_too_short)
                                "psk_too_long" -> stringResource(R.string.real_wifi_probe_psk_too_long)
                                else -> stringResource(R.string.real_wifi_probe_psk_invalid)
                            },
                        )
                    }
                },
            )
            val cooldown = state.cooldownRemainingMs
            Button(
                onClick = onStart,
                enabled = state.canRequestStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (cooldown > 0) {
                        stringResource(R.string.real_wifi_probe_cooldown)
                    } else {
                        stringResource(R.string.real_wifi_probe_start)
                    },
                )
            }
        }
    }
}

@Composable
private fun DenialsCard(denials: List<AuthorizedApTestDenial>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.real_wifi_probe_denied_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.real_wifi_probe_fixed_lab_only))
            denials.forEach { denial ->
                Text("• ${denialLabel(denial)}")
            }
        }
    }
}

@Composable
private fun denialLabel(denial: AuthorizedApTestDenial): String =
    when (denial) {
        AuthorizedApTestDenial.FixedLabNotConfigured ->
            stringResource(R.string.real_wifi_probe_denial_not_configured)
        AuthorizedApTestDenial.FixedLabTargetMismatch ->
            stringResource(R.string.real_wifi_probe_fixed_lab_only)
        AuthorizedApTestDenial.LabModeDisabled -> stringResource(R.string.audit_ap_denial_lab_mode)
        AuthorizedApTestDenial.NotCurrentlyConnected -> stringResource(R.string.audit_ap_denial_not_connected)
        AuthorizedApTestDenial.InsufficientConnectionInfo -> stringResource(R.string.audit_ap_denial_info)
        AuthorizedApTestDenial.ConsentRequired -> stringResource(R.string.audit_ap_denial_consent)
        AuthorizedApTestDenial.UnsupportedFamily -> stringResource(R.string.audit_ap_denial_family)
        AuthorizedApTestDenial.PlatformCapabilityUnavailable ->
            stringResource(R.string.audit_ap_denial_capability)
        else -> denial.name
    }

@Composable
private fun OutcomeCard(outcome: RealWifiProbeUiOutcome) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (outcome) {
                RealWifiProbeUiOutcome.Validated -> {
                    Text(stringResource(R.string.real_wifi_probe_result_validated_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.real_wifi_probe_result_validated_body))
                }
                RealWifiProbeUiOutcome.AuthenticationRejected -> {
                    Text(
                        stringResource(R.string.real_wifi_probe_result_auth_rejected),
                        fontWeight = FontWeight.Bold,
                    )
                }
                RealWifiProbeUiOutcome.Inconclusive -> {
                    Text(
                        stringResource(R.string.real_wifi_probe_result_inconclusive_title),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.real_wifi_probe_result_inconclusive_body))
                }
            }
        }
    }
}

@Composable
private fun LearningSection(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (expanded) {
                        stringResource(R.string.real_wifi_probe_learning_hide)
                    } else {
                        stringResource(R.string.real_wifi_probe_learning_show)
                    },
                )
            }
            if (expanded) {
                Text(stringResource(R.string.real_wifi_probe_learning_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.real_wifi_probe_learning_1))
                Text(stringResource(R.string.real_wifi_probe_learning_2))
                Text(stringResource(R.string.real_wifi_probe_learning_3))
                Text(stringResource(R.string.real_wifi_probe_learning_4))
                Text(stringResource(R.string.real_wifi_probe_learning_5))
                Text(stringResource(R.string.real_wifi_probe_learning_6))
            }
        }
    }
}

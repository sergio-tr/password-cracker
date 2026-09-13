package com.wifiauditlab.android.ui.nearby

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.SignalQuality
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.port.WifiScanState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    viewModel: NearbyViewModel = koinViewModel(),
    onOpenSecurityAnalysis: (NearbyItem) -> Unit = {},
    onOpenLab: (NearbyItem, String?) -> Unit = { _, _ -> },
    onOpenPasswordAudit: (NearbyItem) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissions = scanPermissions()
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nearby_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(
                onClick = {
                    if (state.scanState is WifiScanState.PermissionRequired) {
                        launcher.launch(permissions)
                    } else {
                        viewModel.refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.nearby_refresh)) }

            when (val scan = state.scanState) {
                WifiScanState.PermissionRequired ->
                    StatusWithAction(
                        stringResource(R.string.nearby_permission_required),
                        actionLabel = stringResource(R.string.nearby_grant_permission),
                        onAction = { launcher.launch(permissions) },
                    )
                WifiScanState.LocationServicesDisabled ->
                    StatusWithAction(
                        stringResource(R.string.nearby_location_disabled),
                        actionLabel = stringResource(R.string.nearby_open_location_settings),
                        onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    )
                WifiScanState.Unavailable ->
                    StatusWithAction(
                        stringResource(R.string.nearby_wifi_unavailable),
                        actionLabel = stringResource(R.string.nearby_open_wifi_settings),
                        onAction = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    )
                is WifiScanState.Error -> StatusText(stringResource(R.string.nearby_scan_error, scan.message))
                WifiScanState.Loading -> StatusText(stringResource(R.string.nearby_scanning))
                WifiScanState.Idle -> StatusText(stringResource(R.string.nearby_idle_hint))
                is WifiScanState.Throttled -> {
                    StatusText(stringResource(R.string.nearby_throttled))
                    NetworkList(state.items, onSelect = viewModel::select)
                }
                is WifiScanState.Results -> NetworkList(state.items, onSelect = viewModel::select)
            }
        }
    }

    detail?.let { current ->
        NetworkDetailSheet(
            detail = current,
            onDismiss = viewModel::dismissDetail,
            onSave = viewModel::saveSelectedToVault,
            onOpenSecurityAnalysis = {
                onOpenSecurityAnalysis(current.item)
                viewModel.dismissDetail()
            },
            onOpenLab = {
                onOpenLab(current.item, current.assessment?.headline)
                viewModel.dismissDetail()
            },
            onOpenPasswordAudit = {
                onOpenPasswordAudit(current.item)
                viewModel.dismissDetail()
            },
            onOpenWifiSettings = {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            },
            onRequestPermissions = { launcher.launch(permissions) },
        )
    }
}

@Composable
private fun NetworkList(
    items: List<NearbyItem>,
    onSelect: (NearbyItem) -> Unit,
) {
    if (items.isEmpty()) {
        StatusText(stringResource(R.string.nearby_empty))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(items, key = { it.observation.bssid.value }) { item -> NetworkCard(item, onSelect) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkCard(
    item: NearbyItem,
    onSelect: (NearbyItem) -> Unit,
) {
    val connectedCd = stringResource(R.string.nearby_cd_connected)
    Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.alias ?: item.observation.ssid.toString(),
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.isCurrentlyConnected) {
                        AssistChip(
                            onClick = { onSelect(item) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.nearby_connected)) },
                            modifier = Modifier.semantics { contentDescription = connectedCd },
                        )
                    }
                    AssistChip(
                        onClick = { onSelect(item) },
                        label = {
                            Text(
                                when {
                                    item.ambiguous -> stringResource(R.string.nearby_ambiguous)
                                    item.isKnown -> stringResource(R.string.nearby_saved)
                                    else -> stringResource(R.string.nearby_unknown)
                                },
                            )
                        },
                    )
                }
            }
            if (item.alias != null) Text(item.observation.ssid.toString())
            val band = bandLabel(item.observation.channel.band)
            val quality = qualityLabel(item.observation.signal.quality)
            Text("${item.observation.securityProfile.family.name} · $band · $quality")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailSheet(
    detail: NearbyDetailState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onOpenSecurityAnalysis: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenPasswordAudit: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val observation = detail.item.observation
    var alias by remember(detail.item) {
        mutableStateOf(detail.item.alias ?: observation.ssid.value)
    }
    var showAdvanced by remember(detail.item) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val openLabCd = stringResource(R.string.nearby_cd_open_lab)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(detail.item.alias ?: observation.ssid.toString(), fontWeight = FontWeight.Bold)
            if (detail.item.alias != null) Text(observation.ssid.toString())
            Text(stringResource(R.string.nearby_security, observation.securityProfile.family.name))
            Text(stringResource(R.string.nearby_signal, qualityLabel(observation.signal.quality)))
            Text(
                if (detail.item.isCurrentlyConnected) {
                    stringResource(R.string.nearby_status_connected)
                } else {
                    stringResource(R.string.nearby_status_not_connected)
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    detail.saved || (detail.item.isKnown && !detail.item.ambiguous) ->
                        stringResource(R.string.nearby_vault_saved)
                    detail.item.ambiguous -> stringResource(R.string.nearby_ambiguous_match)
                    else -> stringResource(R.string.nearby_not_saved)
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))
            PasswordAuditSection(
                eligibility = detail.auditEligibility,
                onOpenPasswordAudit = onOpenPasswordAudit,
                onOpenWifiSettings = onOpenWifiSettings,
                onRequestPermissions = onRequestPermissions,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenLab,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = openLabCd },
            ) {
                Icon(
                    Icons.Filled.Science,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.nearby_open_lab))
            }
            OutlinedButton(
                onClick = onOpenSecurityAnalysis,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.nearby_analyze_security)) }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(
                    if (showAdvanced) {
                        stringResource(R.string.nearby_hide_advanced)
                    } else {
                        stringResource(R.string.nearby_show_advanced)
                    },
                )
            }
            if (showAdvanced) {
                Text(
                    stringResource(
                        R.string.nearby_channel_info,
                        bandLabel(observation.channel.band),
                        observation.channel.number,
                        observation.signal.rssiDbm,
                    ),
                )
                Text(stringResource(R.string.nearby_bssid, observation.bssid))
                val assessment = detail.assessment
                if (assessment == null) {
                    Text(stringResource(R.string.nearby_analyzing))
                } else {
                    AssessmentBlock(assessment)
                }
            }

            Spacer(Modifier.height(16.dp))
            if (detail.saved) {
                Text(stringResource(R.string.nearby_vault_saved_confirm), fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.nearby_label_alias)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(alias) }, modifier = Modifier.weight(1f)) {
                        Text(
                            if (detail.item.isKnown && !detail.item.ambiguous) {
                                stringResource(R.string.nearby_update_vault)
                            } else {
                                stringResource(R.string.nearby_save_vault)
                            },
                        )
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nearby_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordAuditSection(
    eligibility: PasswordAuditEligibility?,
    onOpenPasswordAudit: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val auditCta = stringResource(R.string.audit_cta_nearby)
    val openWifiSettings = stringResource(R.string.audit_open_wifi_settings)
    when (eligibility) {
        null -> Text(stringResource(R.string.nearby_checking_eligibility))
        is PasswordAuditEligibility.EligibleConnectedNetwork -> {
            Text(stringResource(R.string.audit_connected_now), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.audit_available), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onOpenPasswordAudit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = auditCta },
            ) {
                Icon(Icons.Filled.WifiLock, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(auditCta)
            }
        }
        PasswordAuditEligibility.NotCurrentlyConnected -> {
            Text(stringResource(R.string.audit_not_connected_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.audit_not_connected_body))
            OutlinedButton(
                onClick = onOpenWifiSettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = openWifiSettings },
            ) { Text(openWifiSettings) }
        }
        is PasswordAuditEligibility.UnsupportedAuthenticationModel -> {
            Text(eligibility.reason)
        }
        PasswordAuditEligibility.MissingPermissions -> {
            Text(stringResource(R.string.nearby_missing_permissions))
            OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nearby_grant_permission))
            }
        }
        PasswordAuditEligibility.InsufficientInformation -> {
            Text(stringResource(R.string.nearby_insufficient_info))
            OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nearby_review_permissions))
            }
        }
    }
}

@Composable
private fun AssessmentBlock(assessment: SecurityAssessment) {
    Text(
        stringResource(
            R.string.nearby_rating_headline,
            ratingLabel(assessment.rating),
            assessment.headline,
        ),
        fontWeight = FontWeight.SemiBold,
    )
    Text(assessment.plainExplanation)
    if (assessment.findings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        assessment.findings.forEach { finding ->
            Text(stringResource(R.string.nearby_finding, finding.title, finding.explanation))
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
    }
}

@Composable
private fun StatusWithAction(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text)
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ratingLabel(rating: SecurityRating): String =
    when (rating) {
        SecurityRating.HIGH -> stringResource(R.string.nearby_rating_high)
        SecurityRating.MODERATE -> stringResource(R.string.nearby_rating_moderate)
        SecurityRating.LOW -> stringResource(R.string.nearby_rating_low)
        SecurityRating.INSECURE -> stringResource(R.string.nearby_rating_insecure)
    }

@Composable
private fun bandLabel(band: WifiBand): String =
    when (band) {
        WifiBand.GHZ_2_4 -> stringResource(R.string.nearby_band_2_4)
        WifiBand.GHZ_5 -> stringResource(R.string.nearby_band_5)
        WifiBand.GHZ_6 -> stringResource(R.string.nearby_band_6)
        WifiBand.UNKNOWN -> stringResource(R.string.nearby_band_unknown)
    }

@Composable
private fun qualityLabel(quality: SignalQuality): String =
    when (quality) {
        SignalQuality.EXCELLENT -> stringResource(R.string.nearby_signal_excellent)
        SignalQuality.GOOD -> stringResource(R.string.nearby_signal_good)
        SignalQuality.FAIR -> stringResource(R.string.nearby_signal_fair)
        SignalQuality.WEAK -> stringResource(R.string.nearby_signal_weak)
    }

private fun scanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

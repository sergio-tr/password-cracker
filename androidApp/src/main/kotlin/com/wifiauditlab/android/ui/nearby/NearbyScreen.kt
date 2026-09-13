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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissions = scanPermissions()
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Redes cercanas") }) }) { padding ->
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
            ) { Text("Actualizar") }

            when (val scan = state.scanState) {
                WifiScanState.PermissionRequired ->
                    StatusWithAction(
                        "Necesitamos permiso para descubrir redes. Pulsa Actualizar para concederlo.",
                        actionLabel = "Conceder permiso",
                        onAction = { launcher.launch(permissions) },
                    )
                WifiScanState.LocationServicesDisabled ->
                    StatusWithAction(
                        "Activa la ubicación del dispositivo para poder escanear redes Wi-Fi.",
                        actionLabel = "Abrir ajustes de ubicación",
                        onAction = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    )
                WifiScanState.Unavailable ->
                    StatusWithAction(
                        "El Wi-Fi no está disponible. Actívalo para buscar redes.",
                        actionLabel = "Abrir ajustes de Wi-Fi",
                        onAction = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    )
                is WifiScanState.Error -> StatusText("Error al escanear: ${scan.message}")
                WifiScanState.Loading -> StatusText("Escaneando…")
                WifiScanState.Idle -> StatusText("Pulsa Actualizar para buscar redes cercanas.")
                is WifiScanState.Throttled -> {
                    StatusText("Escaneo limitado temporalmente. Se muestran los últimos resultados.")
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
        )
    }
}

@Composable
private fun NetworkList(
    items: List<NearbyItem>,
    onSelect: (NearbyItem) -> Unit,
) {
    if (items.isEmpty()) {
        StatusText("No se han encontrado redes todavía.")
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
    Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.alias ?: item.observation.ssid.toString(),
                    fontWeight = FontWeight.SemiBold,
                )
                AssistChip(
                    onClick = { onSelect(item) },
                    label = {
                        Text(
                            when {
                                item.ambiguous -> "Ambigua"
                                item.isKnown -> "Guardada"
                                else -> "Desconocida"
                            },
                        )
                    },
                )
            }
            if (item.alias != null) Text(item.observation.ssid.toString())
            Text(
                buildString {
                    append(item.observation.securityProfile.family.name)
                    append(" · ")
                    append(bandLabel(item.observation.channel.band))
                    append(" · ")
                    append(qualityLabel(item.observation.signal.quality))
                },
            )
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
) {
    val observation = detail.item.observation
    var alias by remember(detail.item) {
        mutableStateOf(detail.item.alias ?: observation.ssid.value)
    }
    var showAdvanced by remember(detail.item) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Text("Seguridad: ${observation.securityProfile.family.name}")
            Text("Señal: ${qualityLabel(observation.signal.quality)}")
            Text(
                when {
                    detail.saved || (detail.item.isKnown && !detail.item.ambiguous) -> "Guardada en el Vault"
                    detail.item.ambiguous -> "Coincidencia ambigua"
                    else -> "No guardada"
                },
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenLab,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Probar en laboratorio" },
            ) {
                Icon(
                    Icons.Filled.Science,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Probar en laboratorio")
            }
            OutlinedButton(
                onClick = onOpenSecurityAnalysis,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Analizar seguridad") }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar datos avanzados" else "Mostrar datos avanzados")
            }
            if (showAdvanced) {
                Text(
                    "${bandLabel(observation.channel.band)} · canal ${observation.channel.number} · " +
                        "${observation.signal.rssiDbm} dBm",
                )
                Text("BSSID: ${observation.bssid}")
                val assessment = detail.assessment
                if (assessment == null) {
                    Text("Analizando seguridad…")
                } else {
                    AssessmentBlock(assessment)
                }
            }

            Spacer(Modifier.height(16.dp))
            if (detail.saved) {
                Text("Guardada en el Vault.", fontWeight = FontWeight.SemiBold)
            } else {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(alias) }, modifier = Modifier.weight(1f)) {
                        Text(
                            if (detail.item.isKnown && !detail.item.ambiguous) {
                                "Actualizar en el Vault"
                            } else {
                                "Guardar en el Vault"
                            },
                        )
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
private fun AssessmentBlock(assessment: SecurityAssessment) {
    Text("${ratingLabel(assessment.rating)} · ${assessment.headline}", fontWeight = FontWeight.SemiBold)
    Text(assessment.plainExplanation)
    if (assessment.findings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        assessment.findings.forEach { finding ->
            Text("• ${finding.title}: ${finding.explanation}")
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

private fun ratingLabel(rating: SecurityRating): String =
    when (rating) {
        SecurityRating.HIGH -> "Seguridad alta"
        SecurityRating.MODERATE -> "Seguridad moderada"
        SecurityRating.LOW -> "Seguridad baja"
        SecurityRating.INSECURE -> "Insegura"
    }

private fun bandLabel(band: WifiBand): String =
    when (band) {
        WifiBand.GHZ_2_4 -> "2,4 GHz"
        WifiBand.GHZ_5 -> "5 GHz"
        WifiBand.GHZ_6 -> "6 GHz"
        WifiBand.UNKNOWN -> "Banda desconocida"
    }

private fun qualityLabel(quality: SignalQuality): String =
    when (quality) {
        SignalQuality.EXCELLENT -> "Excelente"
        SignalQuality.GOOD -> "Buena"
        SignalQuality.FAIR -> "Aceptable"
        SignalQuality.WEAK -> "Débil"
    }

private fun scanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

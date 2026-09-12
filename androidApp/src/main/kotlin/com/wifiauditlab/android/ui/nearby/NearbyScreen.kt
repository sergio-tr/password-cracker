package com.wifiauditlab.android.ui.nearby

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.assessment.domain.wifi.SignalQuality
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.port.WifiScanState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(viewModel: NearbyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissions = scanPermissions()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Redes cercanas") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(
                onClick = {
                    if (state.scanState is WifiScanState.PermissionRequired) launcher.launch(permissions)
                    else viewModel.refresh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Actualizar") }

            when (val scan = state.scanState) {
                WifiScanState.PermissionRequired -> StatusText(
                    "Necesitamos permiso para descubrir redes. Pulsa Actualizar para concederlo.",
                )
                WifiScanState.LocationServicesDisabled -> StatusText(
                    "Activa la ubicación del dispositivo para poder escanear redes Wi-Fi.",
                )
                WifiScanState.Unavailable -> StatusText("El Wi-Fi no está disponible en este dispositivo.")
                is WifiScanState.Error -> StatusText("Error al escanear: ${scan.message}")
                WifiScanState.Loading -> StatusText("Escaneando…")
                WifiScanState.Idle -> StatusText("Pulsa Actualizar para buscar redes cercanas.")
                is WifiScanState.Throttled,
                is WifiScanState.Results -> NetworkList(state.items)
            }
        }
    }
}

@Composable
private fun NetworkList(items: List<NearbyItem>) {
    if (items.isEmpty()) {
        StatusText("No se han encontrado redes todavía.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        items(items, key = { it.observation.bssid.value }) { item -> NetworkCard(item) }
    }
}

@Composable
private fun NetworkCard(item: NearbyItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    item.alias ?: item.observation.ssid.toString(),
                    fontWeight = FontWeight.SemiBold,
                )
                AssistChip(
                    onClick = {},
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

@Composable
private fun StatusText(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
    }
}

private fun bandLabel(band: WifiBand): String = when (band) {
    WifiBand.GHZ_2_4 -> "2,4 GHz"
    WifiBand.GHZ_5 -> "5 GHz"
    WifiBand.GHZ_6 -> "6 GHz"
    WifiBand.UNKNOWN -> "Banda desconocida"
}

private fun qualityLabel(quality: SignalQuality): String = when (quality) {
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

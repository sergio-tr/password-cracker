package com.wifiauditlab.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPermissions: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    calibrationViewModel: SettingsCalibrationViewModel = koinViewModel(),
) {
    val capabilities = AndroidPlatformCapabilities()
    val calibration by calibrationViewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Permisos y servicios", fontWeight = FontWeight.SemiBold)
                    Text("Revisa permisos requeridos, servicios del sistema y capacidades opcionales.")
                    Button(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir centro de permisos")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Onboarding", fontWeight = FontWeight.SemiBold)
                    Text("Vuelve a ver la introducción de Cercanas, Vault y Laboratorio.")
                    OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text("Mostrar introducción")
                    }
                }
            }
            CalibrationCard(
                state = calibration,
                onRecalibrate = calibrationViewModel::recalibrate,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Capacidades de la plataforma", fontWeight = FontWeight.SemiBold)
                    Text("Descubrimiento de redes cercanas: ${capabilities.nearbyWifiDiscovery.toYesNo()}")
                    Text("Inspección de la red actual: ${capabilities.currentWifiInspection.toYesNo()}")
                    Text("Almacenamiento seguro de secretos: ${capabilities.secureSecretStorage.toYesNo()}")
                    Text("Geolocalización: ${capabilities.geolocation.toYesNo()}")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qué hace esta app", fontWeight = FontWeight.SemiBold)
                    Text("Cercanas — descubre redes Wi‑Fi reales y evalúa su seguridad.")
                    Text("Guardadas — el Vault: alias, ubicación y contraseña cifrada.")
                    Text("Laboratorio — busca secretos sintéticos con límites y un botón STOP.")
                    Text(
                        "El laboratorio está aislado de las redes reales: sus algoritmos nunca se conectan a un Wi‑Fi.",
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    state: CalibrationSettingsUiState,
    onRecalibrate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Performance calibration", fontWeight = FontWeight.SemiBold)
            Text("Estado: ${state.statusLabel}")
            Text("Last calibration: ${state.lastCalibrationLabel}")
            Text("Measured throughput: ${state.throughputLabel}")
            Text("Sample duration: ${state.sampleDurationLabel}")
            Text("Fingerprint: ${state.fingerprintLabel}")
            state.errorMessage?.let { Text(it) }
            Button(
                onClick = onRecalibrate,
                enabled = !state.loading && !state.recalibrating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.recalibrating) "Recalibrating…" else "Recalibrate")
            }
        }
    }
}

private fun Boolean.toYesNo(): String = if (this) "Sí" else "No"

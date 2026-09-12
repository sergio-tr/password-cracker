package com.wifiauditlab.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val capabilities = AndroidPlatformCapabilities()
    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

private fun Boolean.toYesNo(): String = if (this) "Sí" else "No"

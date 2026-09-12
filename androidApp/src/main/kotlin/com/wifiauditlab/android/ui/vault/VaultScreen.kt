package com.wifiauditlab.android.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedWifiNetwork?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Redes guardadas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir red")
            }
        },
    ) { padding ->
        if (state.networks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Aún no has guardado ninguna red. Usa el botón + para añadir una.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            ) {
                items(state.networks, key = { it.id.value }) { network ->
                    SavedNetworkCard(
                        network = network,
                        revealedSecret = if (state.revealedSecretId == network.id) state.revealedSecret else null,
                        onReveal = { viewModel.revealSecret(network) },
                        onHide = viewModel::hideSecret,
                        onDelete = { pendingDelete = network },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddNetworkDialog(
            onDismiss = { showAdd = false },
            onConfirm = { alias, ssid, family, location, secret ->
                viewModel.create(alias, ssid, family, location, secret)
                showAdd = false
            },
        )
    }

    pendingDelete?.let { network ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar red") },
            text = { Text("¿Seguro que quieres eliminar \"${network.alias}\"? También se borrará su contraseña.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(network.id)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SavedNetworkCard(
    network: SavedWifiNetwork,
    revealedSecret: String?,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(network.alias, fontWeight = FontWeight.SemiBold)
            Text(network.ssid)
            network.locationLabel?.let { Text("Ubicación: ${it.value}") }
            Text("Seguridad: ${network.securityFamily.name}")
            if (network.hasSecret) {
                Text("Contraseña: ${revealedSecret ?: "••••••••••••"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (revealedSecret == null) {
                        TextButton(onClick = onReveal) { Text("Mostrar") }
                    } else {
                        TextButton(onClick = onHide) { Text("Ocultar") }
                    }
                }
            } else {
                Text("Sin contraseña guardada")
            }
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}

@Composable
private fun AddNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, SecurityFamily, String?, String?) -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val family = SecurityFamily.WPA2_PERSONAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva red guardada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias") })
                OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") })
                OutlinedTextField(location, { location = it }, label = { Text("Ubicación (opcional)") })
                OutlinedTextField(secret, { secret = it }, label = { Text("Contraseña (opcional)") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(alias, ssid, family, location, secret) },
                enabled = alias.isNotBlank() && ssid.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

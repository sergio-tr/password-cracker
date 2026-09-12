package com.wifiauditlab.android.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.assessment.application.SavedNetworkListSort
import com.wifiauditlab.assessment.application.SavedNetworkSecretFilter
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Redes guardadas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir red")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.isEmpty) {
                VaultControls(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onSort = viewModel::setSort,
                    onFilter = viewModel::setFilter,
                )
            }

            when {
                state.isEmpty ->
                    EmptyState("Aún no has guardado ninguna red. Usa el botón + para añadir una.")

                state.networks.isEmpty() ->
                    EmptyState("Ninguna red coincide con la búsqueda o el filtro.")

                else ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    ) {
                        items(state.networks, key = { it.id.value }) { network ->
                            SavedNetworkCard(network = network, onClick = { viewModel.select(network) })
                        }
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

    detail?.let { current ->
        NetworkDetailSheet(
            detail = current,
            viewModel = viewModel,
            onDismiss = viewModel::dismissDetail,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultControls(
    state: VaultUiState,
    onQuery: (String) -> Unit,
    onSort: (SavedNetworkListSort) -> Unit,
    onFilter: (SavedNetworkSecretFilter) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text("Buscar por alias, SSID o ubicación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SavedNetworkSecretFilter.entries.forEach { option ->
                FilterChip(
                    selected = state.filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.filterLabel()) },
                )
            }
            SortMenu(current = state.sort, onSort = onSort)
        }
    }
}

@Composable
private fun SortMenu(
    current: SavedNetworkListSort,
    onSort: (SavedNetworkListSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AssistChip(onClick = { expanded = true }, label = { Text("Orden: ${current.sortLabel()}") })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SavedNetworkListSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.sortLabel()) },
                onClick = {
                    onSort(option)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) { Text(message) }
}

@Composable
private fun SavedNetworkCard(
    network: SavedWifiNetwork,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(network.alias, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(network.ssid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            network.locationLabel?.let { Text("Ubicación: ${it.value}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(securityLabel(network.securityFamily))
                Text(if (network.hasSecret) "· Con contraseña" else "· Sin contraseña")
            }
            network.lastSeenAtEpochMillis?.let { Text("Vista: ${formatLastSeen(it)}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailSheet(
    detail: VaultDetailState,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val network = detail.network

    var editAlias by remember { mutableStateOf(false) }
    var editLocation by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var editSecret by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemoveSecret by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(network.alias, fontWeight = FontWeight.Bold)
            Text(network.ssid)
            Text(securityLabel(network.securityFamily))
            network.locationLabel?.let { Text("Ubicación: ${it.value}") }
            network.lastSeenAtEpochMillis?.let { Text("Vista: ${formatLastSeen(it)}") }
            if (network.knownBssids.isNotEmpty()) {
                Text("Puntos de acceso conocidos: ${network.knownBssids.size}")
            }
            network.notes?.let { Text("Notas: $it") }

            HorizontalDivider()

            Text("Contraseña", fontWeight = FontWeight.SemiBold)
            SecretSection(
                detail = detail,
                onReveal = { viewModel.revealSecret(network) },
                onHide = viewModel::hideSecret,
                onCopy = { detail.revealedSecret?.let { clipboard.copySecret(it) } },
                onReplace = { editSecret = true },
                onRemove = { confirmRemoveSecret = true },
            )

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editAlias = true }) { Text("Editar alias") }
                OutlinedButton(onClick = { editLocation = true }) { Text("Editar ubicación") }
            }
            OutlinedButton(onClick = { editNotes = true }) { Text("Editar notas") }

            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Eliminar red y contraseña") }
        }
    }

    if (editAlias) {
        EditTextDialog(
            title = "Editar alias",
            label = "Alias",
            initial = network.alias,
            allowEmpty = false,
            onDismiss = { editAlias = false },
            onConfirm = {
                viewModel.editAlias(network.id, it)
                editAlias = false
            },
        )
    }
    if (editLocation) {
        EditTextDialog(
            title = "Editar ubicación",
            label = "Ubicación",
            initial = network.locationLabel?.value.orEmpty(),
            allowEmpty = true,
            onDismiss = { editLocation = false },
            onConfirm = {
                viewModel.editLocation(network.id, it)
                editLocation = false
            },
        )
    }
    if (editNotes) {
        EditTextDialog(
            title = "Editar notas",
            label = "Notas",
            initial = network.notes.orEmpty(),
            allowEmpty = true,
            onDismiss = { editNotes = false },
            onConfirm = {
                viewModel.editNotes(network.id, it)
                editNotes = false
            },
        )
    }
    if (editSecret) {
        SecretDialog(
            replacing = network.hasSecret,
            onDismiss = { editSecret = false },
            onConfirm = {
                viewModel.setOrReplaceSecret(network.id, it)
                editSecret = false
            },
        )
    }
    if (confirmRemoveSecret) {
        ConfirmDialog(
            title = "Quitar contraseña",
            message = "Se eliminará solo la contraseña; la red seguirá guardada.",
            confirmLabel = "Quitar",
            onDismiss = { confirmRemoveSecret = false },
            onConfirm = {
                viewModel.removeSecret(network.id)
                confirmRemoveSecret = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar red",
            message = "Se eliminará \"${network.alias}\" y su contraseña. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.delete(network.id)
                confirmDelete = false
            },
        )
    }
}

@Composable
private fun SecretSection(
    detail: VaultDetailState,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCopy: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val network = detail.network
    if (!network.hasSecret) {
        Text("Sin contraseña guardada")
        OutlinedButton(onClick = onReplace) { Text("Añadir contraseña") }
        return
    }

    val revealed = detail.revealedSecret
    Text(
        text = revealed ?: "••••••••••••",
        modifier = Modifier.semantics { contentDescription = if (revealed != null) "Contraseña visible" else "Contraseña oculta" },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (revealed == null) {
            TextButton(onClick = onReveal) { Text("Mostrar") }
        } else {
            TextButton(onClick = onHide) { Text("Ocultar") }
            TextButton(onClick = onCopy) { Text("Copiar") }
        }
        TextButton(onClick = onReplace) { Text("Reemplazar") }
        TextButton(onClick = onRemove) { Text("Quitar") }
    }
}

@Composable
private fun EditTextDialog(
    title: String,
    label: String,
    initial: String,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = allowEmpty || value.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SecretDialog(
    replacing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (replacing) "Reemplazar contraseña" else "Añadir contraseña") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, SecurityFamily, String?, String?) -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var family by remember { mutableStateOf(SecurityFamily.WPA2_PERSONAL) }
    var familyMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva red guardada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias") }, singleLine = true)
                OutlinedTextField(ssid, { ssid = it }, label = { Text("SSID") }, singleLine = true)
                AssistChip(onClick = { familyMenu = true }, label = { Text("Seguridad: ${securityLabel(family)}") })
                DropdownMenu(expanded = familyMenu, onDismissRequest = { familyMenu = false }) {
                    SecurityFamily.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(securityLabel(option)) },
                            onClick = {
                                family = option
                                familyMenu = false
                            },
                        )
                    }
                }
                OutlinedTextField(location, { location = it }, label = { Text("Ubicación (opcional)") }, singleLine = true)
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Contraseña (opcional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
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

private fun ClipboardManager.copySecret(secret: String) {
    setText(AnnotatedString(secret))
}

private fun SavedNetworkSecretFilter.filterLabel(): String =
    when (this) {
        SavedNetworkSecretFilter.All -> "Todas"
        SavedNetworkSecretFilter.WithSecret -> "Con contraseña"
        SavedNetworkSecretFilter.WithoutSecret -> "Sin contraseña"
    }

private fun SavedNetworkListSort.sortLabel(): String =
    when (this) {
        SavedNetworkListSort.AliasAsc -> "Alias A-Z"
        SavedNetworkListSort.LastSeenDesc -> "Vistas recientemente"
        SavedNetworkListSort.SecurityAsc -> "Seguridad"
    }

private fun formatLastSeen(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

private fun securityLabel(family: SecurityFamily): String =
    when (family) {
        SecurityFamily.OPEN -> "Abierta"
        SecurityFamily.WEP -> "WEP"
        SecurityFamily.WPA_PERSONAL -> "WPA"
        SecurityFamily.WPA2_PERSONAL -> "WPA2"
        SecurityFamily.WPA3_PERSONAL -> "WPA3"
        SecurityFamily.WPA2_WPA3_PERSONAL -> "WPA2/WPA3"
        SecurityFamily.WPA2_ENTERPRISE -> "WPA2 Enterprise"
        SecurityFamily.WPA3_ENTERPRISE -> "WPA3 Enterprise"
        SecurityFamily.OWE -> "OWE"
        SecurityFamily.PASSPOINT -> "Passpoint"
        SecurityFamily.DPP -> "DPP"
        SecurityFamily.UNKNOWN -> "Desconocida"
    }

package com.wifiauditlab.android.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.vault_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vault_cd_add))
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
                    EmptyState(stringResource(R.string.vault_empty))

                state.networks.isEmpty() ->
                    EmptyState(stringResource(R.string.vault_no_matches))

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
            label = { Text(stringResource(R.string.vault_search_hint)) },
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
            SavedNetworkListSort.entries.forEach { option ->
                FilterChip(
                    selected = state.sort == option,
                    onClick = { onSort(option) },
                    label = { Text(option.sortLabel()) },
                )
            }
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
            network.locationLabel?.let { Text(stringResource(R.string.vault_location, it.value)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(securityLabel(network.securityFamily))
                Text(
                    if (network.hasSecret) {
                        stringResource(R.string.vault_with_password)
                    } else {
                        stringResource(R.string.vault_without_password)
                    },
                )
            }
            network.lastSeenAtEpochMillis?.let {
                Text(stringResource(R.string.vault_last_seen, formatLastSeen(it)))
            }
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
            network.locationLabel?.let { Text(stringResource(R.string.vault_location, it.value)) }
            network.lastSeenAtEpochMillis?.let {
                Text(stringResource(R.string.vault_last_seen, formatLastSeen(it)))
            }
            if (network.knownBssids.isNotEmpty()) {
                Text(stringResource(R.string.vault_known_aps, network.knownBssids.size))
            }
            network.notes?.let { Text(stringResource(R.string.vault_notes, it)) }

            HorizontalDivider()

            Text(stringResource(R.string.vault_password_section), fontWeight = FontWeight.SemiBold)
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
                OutlinedButton(onClick = { editAlias = true }) {
                    Text(stringResource(R.string.vault_edit_alias))
                }
                OutlinedButton(onClick = { editLocation = true }) {
                    Text(stringResource(R.string.vault_edit_location))
                }
            }
            OutlinedButton(onClick = { editNotes = true }) {
                Text(stringResource(R.string.vault_edit_notes))
            }

            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.vault_delete_network)) }
        }
    }

    if (editAlias) {
        EditTextDialog(
            title = stringResource(R.string.vault_edit_alias),
            label = stringResource(R.string.vault_label_alias),
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
            title = stringResource(R.string.vault_edit_location),
            label = stringResource(R.string.vault_label_location),
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
            title = stringResource(R.string.vault_edit_notes),
            label = stringResource(R.string.vault_label_notes),
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
            title = stringResource(R.string.vault_remove_password_title),
            message = stringResource(R.string.vault_remove_password_message),
            confirmLabel = stringResource(R.string.vault_remove_confirm),
            onDismiss = { confirmRemoveSecret = false },
            onConfirm = {
                viewModel.removeSecret(network.id)
                confirmRemoveSecret = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_title),
            message = stringResource(R.string.vault_delete_message, network.alias),
            confirmLabel = stringResource(R.string.vault_delete_confirm),
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
        Text(stringResource(R.string.vault_no_password))
        OutlinedButton(onClick = onReplace) { Text(stringResource(R.string.vault_add_password)) }
        return
    }

    val revealed = detail.revealedSecret
    val passwordContentDescription =
        if (revealed != null) {
            stringResource(R.string.vault_password_visible)
        } else {
            stringResource(R.string.vault_password_hidden)
        }
    Text(
        text = revealed ?: "••••••••••••",
        modifier = Modifier.semantics { contentDescription = passwordContentDescription },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (revealed == null) {
            TextButton(onClick = onReveal) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.vault_show))
            }
        } else {
            TextButton(onClick = onHide) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.vault_hide))
            }
            TextButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.vault_copy))
            }
        }
        TextButton(onClick = onReplace) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.vault_replace))
        }
        TextButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.vault_remove))
        }
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
            Button(onClick = { onConfirm(value) }, enabled = allowEmpty || value.isNotBlank()) {
                Text(stringResource(R.string.vault_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
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
        title = {
            Text(
                if (replacing) {
                    stringResource(R.string.vault_replace_password)
                } else {
                    stringResource(R.string.vault_add_password_dialog)
                },
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.vault_label_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) {
                Text(stringResource(R.string.vault_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
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
        title = { Text(stringResource(R.string.vault_new_network)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    alias,
                    { alias = it },
                    label = { Text(stringResource(R.string.vault_label_alias)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    ssid,
                    { ssid = it },
                    label = { Text(stringResource(R.string.vault_label_ssid)) },
                    singleLine = true,
                )
                AssistChip(
                    onClick = { familyMenu = true },
                    label = { Text(stringResource(R.string.vault_security, securityLabel(family))) },
                )
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
                OutlinedTextField(
                    location,
                    { location = it },
                    label = { Text(stringResource(R.string.vault_location_optional)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(R.string.vault_password_optional)) },
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
            ) { Text(stringResource(R.string.vault_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) } },
    )
}

private fun ClipboardManager.copySecret(secret: String) {
    setText(AnnotatedString(secret))
}

@Composable
private fun SavedNetworkSecretFilter.filterLabel(): String =
    when (this) {
        SavedNetworkSecretFilter.All -> stringResource(R.string.vault_filter_all)
        SavedNetworkSecretFilter.WithSecret -> stringResource(R.string.vault_filter_with_secret)
        SavedNetworkSecretFilter.WithoutSecret -> stringResource(R.string.vault_filter_without_secret)
    }

@Composable
private fun SavedNetworkListSort.sortLabel(): String =
    when (this) {
        SavedNetworkListSort.AliasAsc -> stringResource(R.string.vault_sort_alias)
        SavedNetworkListSort.LastSeenDesc -> stringResource(R.string.vault_sort_last_seen)
        SavedNetworkListSort.SecurityAsc -> stringResource(R.string.vault_sort_security)
    }

private fun formatLastSeen(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

@Composable
private fun securityLabel(family: SecurityFamily): String =
    when (family) {
        SecurityFamily.OPEN -> stringResource(R.string.vault_sec_open)
        SecurityFamily.WEP -> stringResource(R.string.vault_sec_wep)
        SecurityFamily.WPA_PERSONAL -> stringResource(R.string.vault_sec_wpa)
        SecurityFamily.WPA2_PERSONAL -> stringResource(R.string.vault_sec_wpa2)
        SecurityFamily.WPA3_PERSONAL -> stringResource(R.string.vault_sec_wpa3)
        SecurityFamily.WPA2_WPA3_PERSONAL -> stringResource(R.string.vault_sec_wpa2_wpa3)
        SecurityFamily.WPA2_ENTERPRISE -> stringResource(R.string.vault_sec_wpa2_enterprise)
        SecurityFamily.WPA3_ENTERPRISE -> stringResource(R.string.vault_sec_wpa3_enterprise)
        SecurityFamily.OWE -> stringResource(R.string.vault_sec_owe)
        SecurityFamily.PASSPOINT -> stringResource(R.string.vault_sec_passpoint)
        SecurityFamily.DPP -> stringResource(R.string.vault_sec_dpp)
        SecurityFamily.UNKNOWN -> stringResource(R.string.vault_sec_unknown)
    }

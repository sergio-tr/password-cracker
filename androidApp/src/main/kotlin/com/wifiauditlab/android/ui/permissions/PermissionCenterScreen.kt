package com.wifiauditlab.android.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    viewModel: PermissionCenterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionManager: AndroidWifiPermissionManager = koinInject()
    val scanPermission = permissionManager.requiredPermissions.first()
    var hasRequested by remember { mutableStateOf(false) }

    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.clearPermanentDenial(scanPermission)
            } else {
                val activity = context.findActivity()
                val showRationale =
                    activity != null &&
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, scanPermission)
                if (hasRequested && !showRationale) {
                    viewModel.markPermanentlyDenied(scanPermission)
                } else {
                    viewModel.clearPermanentDenial(scanPermission)
                }
            }
            viewModel.refresh()
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Centro de permisos") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Los permisos se solicitan cuando hacen falta, no todos al arrancar.")
            state.items.forEach { item ->
                PermissionCard(
                    item = item,
                    onRequest = {
                        hasRequested = true
                        requestPermission.launch(scanPermission)
                    },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                )
            }
            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("Actualizar estados")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "Permiso ${item.name}" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold)
            Text("Tipo: ${kindLabel(item.kind)}")
            Text("Estado: ${statusLabel(item.status)}")
            Text(item.rationale)
            when (item.action) {
                PermissionAction.Request -> Button(onClick = onRequest) { Text("Conceder") }
                PermissionAction.OpenAppSettings -> Button(onClick = onOpenAppSettings) { Text("Abrir ajustes") }
                PermissionAction.OpenLocationSettings ->
                    Button(onClick = onOpenLocationSettings) { Text("Abrir ajustes de ubicación") }
                PermissionAction.None -> Unit
            }
        }
    }
}

private fun kindLabel(kind: PermissionKind): String =
    when (kind) {
        PermissionKind.RequiredPermission -> "Permiso requerido"
        PermissionKind.SystemService -> "Servicio del sistema"
        PermissionKind.OptionalCapability -> "Capacidad opcional"
    }

private fun statusLabel(status: PermissionStatus): String =
    when (status) {
        PermissionStatus.Granted -> "Granted"
        PermissionStatus.Missing -> "Missing"
        PermissionStatus.PermanentlyDenied -> "Permanently denied"
        PermissionStatus.Enabled -> "Enabled"
        PermissionStatus.Disabled -> "Disabled"
        PermissionStatus.Available -> "Available"
        PermissionStatus.Unavailable -> "Unavailable"
    }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wifiauditlab.android.R
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun PermissionCenterScreen(
    viewModel: PermissionCenterViewModel = koinViewModel(),
    onBack: () -> Unit = {},
) {
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

    PermissionCenterContent(
        viewModel = viewModel,
        onBack = onBack,
        onRequestPermission = {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterContent(
    viewModel: PermissionCenterViewModel,
    onBack: () -> Unit = {},
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backCd = stringResource(R.string.navigate_back)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backCd },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.permissions_intro))
            state.items.forEach { item ->
                PermissionCard(
                    item = item,
                    onRequest = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenLocationSettings = onOpenLocationSettings,
                )
            }
            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.permissions_refresh))
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
    val name = stringResource(item.nameRes)
    val itemContentDescription = stringResource(R.string.permissions_cd_item, name)
    Card(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = itemContentDescription },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.permissions_kind, kindLabel(item.kind)))
            Text(stringResource(R.string.permissions_status, statusLabel(item.status)))
            Text(stringResource(item.rationaleRes))
            when (item.action) {
                PermissionAction.Request -> Button(onClick = onRequest) { Text(stringResource(R.string.permissions_grant)) }
                PermissionAction.OpenAppSettings ->
                    Button(onClick = onOpenAppSettings) { Text(stringResource(R.string.permissions_open_settings)) }
                PermissionAction.OpenLocationSettings ->
                    Button(onClick = onOpenLocationSettings) {
                        Text(stringResource(R.string.permissions_open_location_settings))
                    }
                PermissionAction.None -> Unit
            }
        }
    }
}

@Composable
private fun kindLabel(kind: PermissionKind): String =
    when (kind) {
        PermissionKind.RequiredPermission -> stringResource(R.string.permissions_kind_required)
        PermissionKind.SystemService -> stringResource(R.string.permissions_kind_system)
        PermissionKind.OptionalCapability -> stringResource(R.string.permissions_kind_optional)
    }

@Composable
private fun statusLabel(status: PermissionStatus): String =
    when (status) {
        PermissionStatus.Granted -> stringResource(R.string.permissions_status_granted)
        PermissionStatus.Missing -> stringResource(R.string.permissions_status_missing)
        PermissionStatus.PermanentlyDenied -> stringResource(R.string.permissions_status_permanently_denied)
        PermissionStatus.Enabled -> stringResource(R.string.permissions_status_enabled)
        PermissionStatus.Disabled -> stringResource(R.string.permissions_status_disabled)
        PermissionStatus.Available -> stringResource(R.string.permissions_status_available)
        PermissionStatus.Unavailable -> stringResource(R.string.permissions_status_unavailable)
    }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

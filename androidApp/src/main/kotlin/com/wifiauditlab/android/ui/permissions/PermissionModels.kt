package com.wifiauditlab.android.ui.permissions

/** How an item relates to app capability. */
enum class PermissionKind {
    RequiredPermission,
    SystemService,
    OptionalCapability,
}

enum class PermissionStatus {
    Granted,
    Missing,
    PermanentlyDenied,
    Enabled,
    Disabled,
    Available,
    Unavailable,
}

enum class PermissionAction {
    Request,
    OpenAppSettings,
    OpenLocationSettings,
    None,
}

data class PermissionItem(
    val id: String,
    val name: String,
    val kind: PermissionKind,
    val status: PermissionStatus,
    val rationale: String,
    val action: PermissionAction,
)

interface PermissionInventory {
    fun refresh(): List<PermissionItem>
}

data class PermissionCenterUiState(
    val items: List<PermissionItem> = emptyList(),
)

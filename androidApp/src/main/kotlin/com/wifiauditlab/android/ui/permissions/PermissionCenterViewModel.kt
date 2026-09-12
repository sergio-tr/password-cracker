package com.wifiauditlab.android.ui.permissions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PermissionCenterViewModel(
    private val inventoryFactory: (permanentlyDenied: Set<String>) -> PermissionInventory,
) : ViewModel() {
    private val permanentlyDenied = mutableSetOf<String>()
    private val _state = MutableStateFlow(PermissionCenterUiState(inventoryFactory(emptySet()).refresh()))
    val state: StateFlow<PermissionCenterUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(items = inventoryFactory(permanentlyDenied.toSet()).refresh()) }
    }

    fun markPermanentlyDenied(permission: String) {
        permanentlyDenied += permission
        refresh()
    }

    fun clearPermanentDenial(permission: String) {
        permanentlyDenied -= permission
        refresh()
    }
}

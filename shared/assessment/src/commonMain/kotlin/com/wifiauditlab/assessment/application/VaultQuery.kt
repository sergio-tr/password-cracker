package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork

/** Coarse filter applied on top of the free-text search. */
enum class SavedNetworkSecretFilter {
    All,
    WithSecret,
    WithoutSecret,
}

/** How the saved-network list is ordered. */
enum class SavedNetworkListSort {
    AliasAsc,
    LastSeenDesc,
    SecurityAsc,
}

/**
 * Presentation-agnostic query over a Vault snapshot: search, secret filter and
 * sort. Lives in the application layer so ViewModels only orchestrate.
 */
class QuerySavedNetworks {
    operator fun invoke(
        networks: List<SavedWifiNetwork>,
        query: String = "",
        filter: SavedNetworkSecretFilter = SavedNetworkSecretFilter.All,
        sort: SavedNetworkListSort = SavedNetworkListSort.AliasAsc,
    ): List<SavedWifiNetwork> = networks.applyFilter(filter).applySearch(query).applySort(sort)

    private fun List<SavedWifiNetwork>.applyFilter(filter: SavedNetworkSecretFilter): List<SavedWifiNetwork> =
        when (filter) {
            SavedNetworkSecretFilter.All -> this
            SavedNetworkSecretFilter.WithSecret -> filter { it.hasSecret }
            SavedNetworkSecretFilter.WithoutSecret -> filter { !it.hasSecret }
        }

    private fun List<SavedWifiNetwork>.applySearch(query: String): List<SavedWifiNetwork> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return this
        return filter { network ->
            network.alias.lowercase().contains(needle) ||
                network.ssid.lowercase().contains(needle) ||
                (network.locationLabel?.value?.lowercase()?.contains(needle) == true)
        }
    }

    private fun List<SavedWifiNetwork>.applySort(sort: SavedNetworkListSort): List<SavedWifiNetwork> =
        when (sort) {
            SavedNetworkListSort.AliasAsc -> sortedBy { it.alias.lowercase() }
            SavedNetworkListSort.LastSeenDesc ->
                sortedByDescending { it.lastSeenAtEpochMillis ?: Long.MIN_VALUE }
            SavedNetworkListSort.SecurityAsc -> sortedBy { it.securityFamily.ordinal }
        }
}

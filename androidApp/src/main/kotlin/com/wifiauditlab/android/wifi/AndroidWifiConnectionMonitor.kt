package com.wifiauditlab.android.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.WifiConnectionMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Emits the current Wi‑Fi association when default network availability/capabilities change.
 * Used by LAB sessions to fail-closed on NETWORK_CHANGED / NETWORK_LOST (F1).
 */
class AndroidWifiConnectionMonitor(
    context: Context,
    private val connectionProvider: CurrentWifiConnectionProvider,
) : WifiConnectionMonitor {
    private val appContext = context.applicationContext

    override fun observe(): Flow<CurrentWifiConnection?> =
        callbackFlow {
            val cm =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(null)
                awaitClose { }
                return@callbackFlow
            }

            suspend fun emitCurrent() {
                trySend(connectionProvider.currentConnection())
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        launch { emitCurrent() }
                    }

                    override fun onLost(network: Network) {
                        trySend(null)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        launch { emitCurrent() }
                    }
                }

            runCatching {
                cm.registerDefaultNetworkCallback(callback)
            }.onFailure {
                trySend(null)
            }
            launch { emitCurrent() }

            awaitClose {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
}

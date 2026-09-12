package com.wifiauditlab.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifiauditlab.android.ui.lab.LabScreen
import com.wifiauditlab.android.ui.nearby.NearbyScreen
import com.wifiauditlab.android.ui.settings.SettingsScreen
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.android.ui.vault.VaultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiAuditLabTheme {
                AppRoot()
            }
        }
    }
}

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Nearby("nearby", "Redes", Icons.Filled.Wifi),
    Vault("vault", "Guardadas", Icons.Filled.Lock),
    Lab("lab", "Laboratorio", Icons.Filled.Science),
    Settings("settings", "Ajustes", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Nearby.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Nearby.route) { NearbyScreen() }
            composable(Destination.Vault.route) { VaultScreen() }
            composable(Destination.Lab.route) { LabScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

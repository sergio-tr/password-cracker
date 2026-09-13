package com.wifiauditlab.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifiauditlab.android.ui.audit.PasswordAuditScreen
import com.wifiauditlab.android.ui.audit.PasswordAuditTargetStore
import com.wifiauditlab.android.ui.audit.passwordAuditRequestFromNearby
import com.wifiauditlab.android.ui.lab.LabNetworkContextStore
import com.wifiauditlab.android.ui.lab.LabScreen
import com.wifiauditlab.android.ui.lab.labNetworkContextFromNearby
import com.wifiauditlab.android.ui.nearby.NearbyScreen
import com.wifiauditlab.android.ui.onboarding.OnboardingScreen
import com.wifiauditlab.android.ui.permissions.PermissionCenterScreen
import com.wifiauditlab.android.ui.security.SecurityAnalysisRequest
import com.wifiauditlab.android.ui.security.SecurityAnalysisScreen
import com.wifiauditlab.android.ui.security.SecurityAnalysisTargetStore
import com.wifiauditlab.android.ui.settings.SettingsScreen
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.android.ui.vault.VaultScreen
import com.wifiauditlab.assessment.port.OnboardingPreferences
import org.koin.compose.koinInject

/**
 * [AppCompatActivity] is required so [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * updates the Activity configuration that Compose [stringResource] reads.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WifiAuditLabTheme {
                AppRoot()
            }
        }
    }
}

private enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Nearby("nearby", R.string.nav_nearby, Icons.Filled.Wifi),
    Vault("vault", R.string.nav_vault, Icons.Filled.Lock),
    Lab("lab", R.string.nav_lab, Icons.Filled.Science),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

private object Routes {
    const val PERMISSIONS = "permissions"
    const val SECURITY_ANALYSIS = "security_analysis"
    const val PASSWORD_AUDIT = "password_audit"
}

@Composable
private fun AppRoot() {
    val onboardingPreferences: OnboardingPreferences = koinInject()
    val analysisTarget: SecurityAnalysisTargetStore = koinInject()
    val labContext: LabNetworkContextStore = koinInject()
    val passwordAuditTarget: PasswordAuditTargetStore = koinInject()
    var showOnboarding by remember { mutableStateOf(!onboardingPreferences.isCompleted()) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val hideBottomBar =
        showOnboarding ||
            currentDestination?.route == Routes.PERMISSIONS ||
            currentDestination?.route == Routes.SECURITY_ANALYSIS ||
            currentDestination?.route == Routes.PASSWORD_AUDIT

    if (showOnboarding) {
        OnboardingScreen(
            replay = onboardingPreferences.isCompleted(),
            onFinished = { showOnboarding = false },
        )
        return
    }

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination == Destination.Lab) {
                                    labContext.clear()
                                }
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Nearby.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Nearby.route) {
                NearbyScreen(
                    onOpenSecurityAnalysis = { item ->
                        analysisTarget.set(
                            SecurityAnalysisRequest(
                                displayName = item.alias ?: item.observation.ssid.toString(),
                                ssidLabel = item.observation.ssid.toString(),
                                profile = item.observation.securityProfile,
                            ),
                        )
                        navController.navigate(Routes.SECURITY_ANALYSIS)
                    },
                    onOpenLab = { item, assessmentSummary ->
                        labContext.set(labNetworkContextFromNearby(item, assessmentSummary))
                        navController.navigate(Destination.Lab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPasswordAudit = { item ->
                        passwordAuditTarget.set(passwordAuditRequestFromNearby(item))
                        navController.navigate(Routes.PASSWORD_AUDIT)
                    },
                )
            }
            composable(Destination.Vault.route) { VaultScreen() }
            composable(Destination.Lab.route) { LabScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onReplayOnboarding = {
                        showOnboarding = true
                    },
                )
            }
            composable(Routes.PERMISSIONS) { PermissionCenterScreen() }
            composable(Routes.SECURITY_ANALYSIS) {
                SecurityAnalysisScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PASSWORD_AUDIT) {
                PasswordAuditScreen(
                    onBack = {
                        passwordAuditTarget.clear()
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

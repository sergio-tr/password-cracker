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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wifiauditlab.android.lab.LabModeSurfaces
import com.wifiauditlab.android.lab.RealWifiProbeFeature
import com.wifiauditlab.android.ui.lab.LabNetworkContextStore
import com.wifiauditlab.android.ui.lab.LabPasswordSeedStore
import com.wifiauditlab.android.ui.lab.LabScreen
import com.wifiauditlab.android.ui.lab.labNetworkContextFromNearby
import com.wifiauditlab.android.ui.lab.labNetworkContextFromVault
import com.wifiauditlab.android.ui.nearby.NearbyScreen
import com.wifiauditlab.android.ui.onboarding.OnboardingScreen
import com.wifiauditlab.android.ui.permissions.PermissionCenterScreen
import com.wifiauditlab.android.ui.realaudit.RealWifiAuditScreen
import com.wifiauditlab.android.ui.realaudit.RealWifiAuditTargetStore
import com.wifiauditlab.android.ui.realaudit.realWifiAuditTargetFromNearby
import com.wifiauditlab.android.ui.realaudit.realWifiAuditTargetFromVault
import com.wifiauditlab.android.ui.realwifi.RealWifiProbeScreen
import com.wifiauditlab.android.ui.security.SecurityAnalysisRequest
import com.wifiauditlab.android.ui.security.SecurityAnalysisScreen
import com.wifiauditlab.android.ui.security.SecurityAnalysisTargetStore
import com.wifiauditlab.android.ui.settings.SettingsScreen
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.android.ui.vault.VaultScreen
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPointProvider
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.port.LabModePreferences
import com.wifiauditlab.assessment.port.OnboardingPreferences
import org.koin.compose.koinInject

/**
 * [AppCompatActivity] is required so [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * updates the Activity configuration that Compose [stringResource] reads.
 *
 * Product branding: visible name is **WiFiSearcher** (`R.string.app_name`).
 * Technical package/applicationId remain `com.wifiauditlab.android` historically.
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
    val requiresLabMode: Boolean = false,
) {
    Nearby("nearby", R.string.nav_nearby, Icons.Filled.Wifi),
    Vault("vault", R.string.nav_vault, Icons.Filled.Lock),
    Lab("lab", R.string.nav_lab, Icons.Filled.Science, requiresLabMode = true),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

/**
 * Navigation chrome (FIX-02):
 * - TOP LEVEL (bottom nav): no [ArrowBack]; user switches tabs.
 * - CHILD (permissions, security_analysis, real_wifi_audit, real_wifi_probe):
 *   [ArrowBack] + [R.string.navigate_back].
 * Lab tab is present only while Lab Mode is enabled.
 */
private object Routes {
    const val PERMISSIONS = "permissions"
    const val SECURITY_ANALYSIS = "security_analysis"
    const val REAL_WIFI_AUDIT = "real_wifi_audit"
    const val REAL_WIFI_PROBE = "real_wifi_probe"
}

@Composable
private fun AppRoot() {
    val onboardingPreferences: OnboardingPreferences = koinInject()
    val labModePreferences: LabModePreferences = koinInject()
    val labModeEnabled by labModePreferences.enabled.collectAsStateWithLifecycle()
    val analysisTarget: SecurityAnalysisTargetStore = koinInject()
    val labContext: LabNetworkContextStore = koinInject()
    val labPasswordSeed: LabPasswordSeedStore = koinInject()
    val realWifiAuditTarget: RealWifiAuditTargetStore = koinInject()
    val fixedLabProvider: FixedLabAccessPointProvider = koinInject()
    val realWifiProbeEntryAvailable =
        RealWifiProbeFeature.isCompiledIn && fixedLabProvider.configuredOrNull() != null
    var showOnboarding by remember { mutableStateOf(!onboardingPreferences.isCompleted()) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelDestinations =
        remember(labModeEnabled) {
            Destination.entries.filter {
                !it.requiresLabMode || LabModeSurfaces.showLabTab(labModeEnabled)
            }
        }
    val hideBottomBar =
        showOnboarding ||
            currentDestination?.route == Routes.PERMISSIONS ||
            currentDestination?.route == Routes.SECURITY_ANALYSIS ||
            currentDestination?.route == Routes.REAL_WIFI_AUDIT ||
            currentDestination?.route == Routes.REAL_WIFI_PROBE

    LaunchedEffect(labModeEnabled, currentDestination?.route) {
        val onLabSurface =
            currentDestination?.route == Destination.Lab.route ||
                currentDestination?.route == Routes.REAL_WIFI_PROBE
        if (!labModeEnabled && onLabSurface) {
            labContext.clear()
            labPasswordSeed.clear()
            navController.navigate(LabModeSurfaces.SAFE_TOP_LEVEL_AFTER_LAB_HIDDEN) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                    topLevelDestinations.forEach { destination ->
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
                        if (!LabModeSurfaces.showLabEntryCtas(labModePreferences.isLabModeEnabled())) {
                            return@NearbyScreen
                        }
                        labPasswordSeed.clear()
                        labContext.set(labNetworkContextFromNearby(item, assessmentSummary))
                        navController.navigate(Destination.Lab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenRealWifiAudit = { item ->
                        realWifiAuditTarget.set(realWifiAuditTargetFromNearby(item))
                        navController.navigate(Routes.REAL_WIFI_AUDIT)
                    },
                )
            }
            composable(Destination.Vault.route) {
                VaultScreen(
                    onOpenLab = { network, revealedPassword ->
                        if (!LabModeSurfaces.showLabEntryCtas(labModePreferences.isLabModeEnabled())) {
                            return@VaultScreen
                        }
                        labContext.set(labNetworkContextFromVault(network))
                        if (!revealedPassword.isNullOrEmpty()) {
                            labPasswordSeed.set(revealedPassword)
                        } else {
                            labPasswordSeed.clear()
                        }
                        navController.navigate(Destination.Lab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenRealWifiAudit = { network ->
                        labPasswordSeed.clear()
                        realWifiAuditTarget.set(
                            realWifiAuditTargetFromVault(
                                network,
                                WifiSecurityProfile(
                                    family = network.securityFamily,
                                    keyManagements = emptySet(),
                                    managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                                    isTransitionMode = false,
                                    rawCapabilities = null,
                                ),
                            ),
                        )
                        navController.navigate(Routes.REAL_WIFI_AUDIT)
                    },
                )
            }
            composable(Destination.Lab.route) {
                if (LabModeSurfaces.showLabTab(labModeEnabled)) {
                    LabScreen(
                        onOpenRealWifiProbe =
                            if (realWifiProbeEntryAvailable) {
                                { navController.navigate(Routes.REAL_WIFI_PROBE) }
                            } else {
                                null
                            },
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.navigate(LabModeSurfaces.SAFE_TOP_LEVEL_AFTER_LAB_HIDDEN) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onReplayOnboarding = {
                        showOnboarding = true
                    },
                )
            }
            composable(Routes.PERMISSIONS) {
                PermissionCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SECURITY_ANALYSIS) {
                SecurityAnalysisScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REAL_WIFI_AUDIT) {
                RealWifiAuditScreen(
                    onBack = {
                        realWifiAuditTarget.clear()
                        navController.popBackStack()
                    },
                )
            }
            if (RealWifiProbeFeature.isCompiledIn) {
                composable(Routes.REAL_WIFI_PROBE) {
                    RealWifiProbeScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

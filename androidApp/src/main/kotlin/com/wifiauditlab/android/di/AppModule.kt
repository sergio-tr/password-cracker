package com.wifiauditlab.android.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wifiauditlab.android.platform.AndroidCalibrationEnvironmentProvider
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import com.wifiauditlab.android.platform.KeystoreSecretVault
import com.wifiauditlab.android.platform.SharedPreferencesBenchmarkRepository
import com.wifiauditlab.android.platform.SharedPreferencesCalibrationRepository
import com.wifiauditlab.android.platform.SharedPreferencesOnboardingPreferences
import com.wifiauditlab.android.ui.audit.PasswordAuditTargetStore
import com.wifiauditlab.android.ui.audit.PasswordAuditViewModel
import com.wifiauditlab.android.ui.lab.LabNetworkContextStore
import com.wifiauditlab.android.ui.lab.LabPasswordSeedStore
import com.wifiauditlab.android.ui.lab.LabViewModel
import com.wifiauditlab.android.ui.nearby.NearbyViewModel
import com.wifiauditlab.android.ui.onboarding.OnboardingViewModel
import com.wifiauditlab.android.ui.permissions.AndroidPermissionInventory
import com.wifiauditlab.android.ui.permissions.PermissionCenterViewModel
import com.wifiauditlab.android.ui.security.SecurityAnalysisTargetStore
import com.wifiauditlab.android.ui.security.SecurityAnalysisViewModel
import com.wifiauditlab.android.ui.settings.SettingsBenchmarkViewModel
import com.wifiauditlab.android.ui.settings.SettingsCalibrationViewModel
import com.wifiauditlab.android.ui.vault.VaultViewModel
import com.wifiauditlab.android.wifi.AndroidCurrentWifiConnectionProvider
import com.wifiauditlab.android.wifi.AndroidWifiMapper
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager
import com.wifiauditlab.android.wifi.AndroidWifiScanner
import com.wifiauditlab.android.wifi.hasConnectionInspectionPermission
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.MatchKnownNetwork
import com.wifiauditlab.assessment.application.ObserveNearbyNetworks
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.application.QuerySavedNetworks
import com.wifiauditlab.assessment.application.RecordNearbySightings
import com.wifiauditlab.assessment.application.RecordSavedNetworkSighting
import com.wifiauditlab.assessment.application.RefreshNearbyNetworks
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.SaveNearbyNetwork
import com.wifiauditlab.assessment.application.SearchSavedNetworks
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.application.UpdateSavedNetworkLocation
import com.wifiauditlab.assessment.application.UpdateSavedNetworkNotes
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.ConnectionInspectionPermissionGate
import com.wifiauditlab.assessment.domain.audit.DefaultPasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.SecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.match.DefaultKnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.KnownNetworkMatcher
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.OnboardingPreferences
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.assessment.port.WifiScanner
import com.wifiauditlab.lab.domain.BenchmarkRepository
import com.wifiauditlab.lab.domain.CalibrationRepository
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.engine.CalibrationEnvironmentProvider
import com.wifiauditlab.lab.domain.engine.LabBenchmarkService
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.engine.CalibratedThroughputEstimator
import com.wifiauditlab.lab.engine.DefaultLabBenchmarkService
import com.wifiauditlab.lab.engine.DefaultSearchCalibrationService
import com.wifiauditlab.lab.engine.DefaultSearchFeasibilityAnalyzer
import com.wifiauditlab.lab.engine.SearchStrategyRegistry
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import com.wifiauditlab.persistence.SqlDelightSavedNetworkRepository
import com.wifiauditlab.persistence.db.VaultDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        // Platform + adapters
        single { AndroidPlatformCapabilities() }
        single { WifiSecurityClassifier() }
        single { AndroidWifiMapper(get()) }
        single { AndroidWifiPermissionManager(androidContext()) }
        single<WifiScanner> { AndroidWifiScanner(androidContext(), get(), get()) }
        single<CurrentWifiConnectionProvider> {
            AndroidCurrentWifiConnectionProvider(androidContext(), get())
        }
        single<NetworkConnectionMatcher> { DefaultNetworkConnectionMatcher() }
        single<PasswordAuditEligibilityChecker> {
            val permissions: AndroidWifiPermissionManager = get()
            DefaultPasswordAuditEligibilityChecker(
                connectionProvider = get(),
                connectionMatcher = get(),
                permissionGate = ConnectionInspectionPermissionGate { permissions.hasConnectionInspectionPermission() },
            )
        }
        single<SecretVault> { KeystoreSecretVault(androidContext()) }
        single<OnboardingPreferences> { SharedPreferencesOnboardingPreferences(androidContext()) }
        single { SecurityAnalysisTargetStore() }
        single { LabNetworkContextStore() }
        single { LabPasswordSeedStore() }
        single { PasswordAuditTargetStore() }
        single<AutomaticPasswordAuditPlanner> { DefaultAutomaticPasswordAuditPlanner() }
        single<SecretStrengthAnalyzer> { HeuristicSecretStrengthAnalyzer() }
        single<SqlDriver> { AndroidSqliteDriver(VaultDatabase.Schema, androidContext(), "vault.db") }
        single { VaultDatabase(get()) }
        single<SavedNetworkRepository> {
            SqlDelightSavedNetworkRepository(
                database = get(),
                dispatcher = Dispatchers.IO,
                nowMillis = { System.currentTimeMillis() },
            )
        }

        // Domain services
        single<KnownNetworkMatcher> { DefaultKnownNetworkMatcher() }
        single { SecurityAssessmentRegistry.default() }

        // Lab engine + durable calibration
        single<SearchPlanOptimizer> { SearchStrategyRegistry() }
        single<LabSearchEngine> {
            WorkerAwareLabSearchEngine(availableProcessors = Runtime.getRuntime().availableProcessors())
        }
        single<SearchFeasibilityAnalyzer> { DefaultSearchFeasibilityAnalyzer() }
        single<SearchPerformanceEstimator> { CalibratedThroughputEstimator() }
        single<CalibrationRepository> { SharedPreferencesCalibrationRepository(androidContext()) }
        single<CalibrationEnvironmentProvider> { AndroidCalibrationEnvironmentProvider(androidContext()) }
        single<SearchCalibrationService> {
            DefaultSearchCalibrationService(
                repository = get(),
                environmentProvider = get(),
                nowMillis = { System.currentTimeMillis() },
            )
        }
        single<BenchmarkRepository> { SharedPreferencesBenchmarkRepository(androidContext()) }
        single<LabBenchmarkService> {
            DefaultLabBenchmarkService(
                repository = get(),
                optimizer = get(),
                nowMillis = { System.currentTimeMillis() },
                availableProcessors = Runtime.getRuntime().availableProcessors(),
            )
        }

        // Use cases
        factory { CreateSavedNetwork(get(), get()) }
        factory { GetSavedNetwork(get()) }
        factory { ObserveSavedNetworks(get()) }
        factory { UpdateSavedNetworkAlias(get()) }
        factory { UpdateSavedNetworkLocation(get()) }
        factory { UpdateSavedNetworkNotes(get()) }
        factory { UpdateSavedNetworkSecret(get(), get()) }
        factory { RemoveSavedNetworkSecret(get(), get()) }
        factory { DeleteSavedNetwork(get(), get()) }
        factory { QuerySavedNetworks() }
        factory { SearchSavedNetworks(get(), get()) }
        factory { RevealSavedNetworkSecret(get(), get()) }
        factory { RecordSavedNetworkSighting(get()) }
        factory { RecordNearbySightings(get()) }
        factory { SaveNearbyNetwork(get(), get(), get()) }
        factory { MatchKnownNetwork(get(), get()) }
        factory { AssessNetworkSecurity(get()) }
        factory { ObserveNearbyNetworks(get(), get(), get()) }
        factory { RefreshNearbyNetworks(get()) }

        // ViewModels
        viewModel { NearbyViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel {
            VaultViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        viewModel {
            LabViewModel(
                get(),
                get(),
                get(),
                get(),
                assessNetworkSecurity = get(),
                planner = get(),
                calibration = get(),
                networkContextStore = get(),
                passwordSeedStore = get(),
            )
        }
        viewModel { OnboardingViewModel(get()) }
        viewModel { SecurityAnalysisViewModel(get(), get()) }
        viewModel {
            PasswordAuditViewModel(
                targetStore = get(),
                planner = get(),
                getSavedNetwork = get(),
                revealSecret = get(),
                updateSecret = get(),
                createSavedNetwork = get(),
                eligibilityChecker = get(),
                assessNetworkSecurity = get(),
                engine = get(),
                calibration = get(),
                strengthAnalyzer = get(),
            )
        }
        viewModel { SettingsCalibrationViewModel(get(), get()) }
        viewModel { SettingsBenchmarkViewModel(get()) }
        viewModel {
            PermissionCenterViewModel(
                inventoryFactory = { permanentDenials ->
                    AndroidPermissionInventory(
                        context = androidContext(),
                        permissionManager = get(),
                        permanentlyDenied = { permanentDenials },
                    )
                },
            )
        }
    }

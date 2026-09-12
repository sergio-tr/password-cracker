package com.wifiauditlab.android.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import com.wifiauditlab.android.platform.KeystoreSecretVault
import com.wifiauditlab.android.ui.lab.LabViewModel
import com.wifiauditlab.android.ui.nearby.NearbyViewModel
import com.wifiauditlab.android.ui.vault.VaultViewModel
import com.wifiauditlab.android.wifi.AndroidWifiMapper
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager
import com.wifiauditlab.android.wifi.AndroidWifiScanner
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.MatchKnownNetwork
import com.wifiauditlab.assessment.application.ObserveNearbyNetworks
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.application.RefreshNearbyNetworks
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.SearchSavedNetworks
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.application.UpdateSavedNetworkLocation
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.classifier.WifiSecurityClassifier
import com.wifiauditlab.assessment.domain.match.DefaultKnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.KnownNetworkMatcher
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.assessment.port.WifiScanner
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.engine.DefaultLabSearchEngine
import com.wifiauditlab.lab.engine.DefaultSearchFeasibilityAnalyzer
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
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
        single<SecretVault> { KeystoreSecretVault(androidContext()) }
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

        // Lab engine
        single<SearchPlanOptimizer> { DefaultSearchPlanOptimizer() }
        single<LabSearchEngine> { DefaultLabSearchEngine() }
        single<SearchFeasibilityAnalyzer> { DefaultSearchFeasibilityAnalyzer() }
        single<SearchPerformanceEstimator> { FixedThroughputEstimator() }

        // Use cases
        factory { CreateSavedNetwork(get(), get()) }
        factory { GetSavedNetwork(get()) }
        factory { ObserveSavedNetworks(get()) }
        factory { UpdateSavedNetworkAlias(get()) }
        factory { UpdateSavedNetworkLocation(get()) }
        factory { UpdateSavedNetworkSecret(get(), get()) }
        factory { RemoveSavedNetworkSecret(get(), get()) }
        factory { DeleteSavedNetwork(get(), get()) }
        factory { SearchSavedNetworks(get()) }
        factory { MatchKnownNetwork(get(), get()) }
        factory { AssessNetworkSecurity(get()) }
        factory { ObserveNearbyNetworks(get(), get(), get()) }
        factory { RefreshNearbyNetworks(get()) }

        // ViewModels
        viewModel { NearbyViewModel(get(), get(), get(), get()) }
        viewModel { VaultViewModel(get(), get(), get(), get()) }
        viewModel { LabViewModel(get(), get(), get(), get()) }
    }

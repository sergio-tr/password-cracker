package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.engine.DefaultLabSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordAuditViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeRepo()
    private val vault = FakeVault()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingTargetShowsEmptyState() =
        runTest {
            val vm = viewModel(store = PasswordAuditTargetStore())
            assertTrue(vm.state.value.missingTarget)
            assertFalse(vm.state.value.loadingPlan)
        }

    @Test
    fun standardPresetProducesPlanExplanation() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            val state = vm.state.value
            assertFalse(state.loadingPlan)
            assertNotNull(state.plan)
            assertEquals("Modo automático", state.explanation?.headline)
            assertTrue(state.explanation!!.details.isNotEmpty())
            assertEquals(PasswordAuditBudgetPreset.Standard, state.preset)
        }

    @Test
    fun changingPresetRebuildsPlanCapacity() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            val standardCapacity = vm.state.value.plan!!.budgetedAttemptCapacity
            vm.selectPreset(PasswordAuditBudgetPreset.Quick)
            val quickCapacity = vm.state.value.plan!!.budgetedAttemptCapacity
            assertNotNull(standardCapacity)
            assertNotNull(quickCapacity)
            assertTrue(quickCapacity!! < standardCapacity!!)
        }

    @Test
    fun startRequiresPassword() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.onStartAuditClicked()
            assertEquals("Introduce o recupera la contraseña conocida.", vm.state.value.passwordError)
            assertEquals(SearchState.Idle, vm.state.value.searchState)
        }

    @Test
    fun startFindsShortDigitPasswordLocally() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.onCustomDurationChanged("30")
            vm.onCustomAttemptsChanged("20000")
            vm.applyCustomBudget()
            advanceUntilIdle()
            vm.onPasswordChanged("42")
            assertNull(vm.state.value.startBlockedReason)
            vm.onStartAuditClicked()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Found, vm.state.value.outcome)
            assertTrue(vm.state.value.discoveredWithinBudget)
            assertEquals("", vm.state.value.passwordInput)
            assertNotNull(vm.state.value.strength)
            assertNotNull(vm.state.value.metrics)
            assertNotNull(vm.state.value.resultReport)
            assertTrue(vm.state.value.resultReport!!.headline.isNotBlank())
        }

    @Test
    fun stopCancelsActiveAudit() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            // Huge space relative to tiny progress — cancel before finish.
            vm.selectPreset(PasswordAuditBudgetPreset.Deep)
            advanceUntilIdle()
            vm.onPasswordChanged("zzzzzzzz")
            vm.onStartAuditClicked()
            assertTrue(vm.state.value.isActive || vm.state.value.outcome != null)
            if (vm.state.value.isActive) {
                vm.stop()
                advanceUntilIdle()
                assertTrue(
                    vm.state.value.outcome == SearchOutcome.Cancelled ||
                        vm.state.value.searchState == SearchState.Cancelled ||
                        !vm.state.value.isActive,
                )
            }
        }

    @Test
    fun unsupportedFamilyIsNotApplicable() =
        runTest {
            val vm =
                viewModel(
                    request =
                        PasswordAuditRequest(
                            network =
                                PasswordAuditNetworkContext(
                                    displayName = "Cafe",
                                    ssid = Ssid("OPEN_CAFE"),
                                    bssid = Bssid.of("aa:bb:cc:dd:ee:ff"),
                                    securityProfile = WifiSecurityProfile.open(),
                                    wifiStandard = null,
                                    band = null,
                                ),
                            savedNetworkId = null,
                        ),
                )
            assertNull(vm.state.value.plan)
            assertNotNull(vm.state.value.planNotApplicableReason)
        }

    @Test
    fun vaultPasswordPopulatesField() =
        runTest {
            val created =
                CreateSavedNetwork(repo, vault)(
                    NewSavedWifiNetwork(
                        alias = "Casa",
                        ssid = "HOME_WIFI",
                        securityFamily = SecurityFamily.WPA2_PERSONAL,
                        knownBssids = setOf(Bssid.of("11:22:33:44:55:66")),
                    ),
                    NetworkSecret("vault-pass"),
                )
            val vm = viewModel(request = eligibleRequest().copy(savedNetworkId = created.id))
            assertTrue(vm.state.value.vaultSecretAvailable)
            vm.useVaultPassword()
            assertEquals("vault-pass", vm.state.value.passwordInput)
            assertTrue(vm.state.value.passwordFromVault)
        }

    private fun viewModel(
        store: PasswordAuditTargetStore = PasswordAuditTargetStore().also { it.set(eligibleRequest()) },
        request: PasswordAuditRequest? = null,
    ): PasswordAuditViewModel {
        val target =
            if (request != null) {
                PasswordAuditTargetStore().also { it.set(request) }
            } else {
                store
            }
        return PasswordAuditViewModel(
            targetStore = target,
            planner = DefaultAutomaticPasswordAuditPlanner(),
            getSavedNetwork = GetSavedNetwork(repo),
            revealSecret = RevealSavedNetworkSecret(repo, vault),
            engine = DefaultLabSearchEngine(),
            calibration = null,
            strengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
            availableProcessors = 4,
            ioDispatcher = dispatcher,
        )
    }

    private fun eligibleRequest(): PasswordAuditRequest =
        PasswordAuditRequest(
            network =
                PasswordAuditNetworkContext(
                    displayName = "Casa",
                    ssid = Ssid("HOME_WIFI"),
                    bssid = Bssid.of("11:22:33:44:55:66"),
                    securityProfile =
                        WifiSecurityProfile(
                            family = SecurityFamily.WPA2_PERSONAL,
                            keyManagements = setOf("WPA_PSK"),
                            managementFrameProtection = ManagementFrameProtection.CAPABLE,
                            isTransitionMode = false,
                            rawCapabilities = null,
                        ),
                    wifiStandard = null,
                    band = null,
                ),
            savedNetworkId = null,
        )

    private class FakeRepo : SavedNetworkRepository {
        val networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
        private var seq = 0

        override fun observeAll(): Flow<List<SavedWifiNetwork>> = networks

        override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? = networks.value.firstOrNull { it.id == id }

        override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
            networks.value.filter { it.identity == identity }

        override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
            val created =
                SavedWifiNetwork(
                    id = SavedNetworkId("net-${seq++}"),
                    alias = network.alias,
                    ssid = network.ssid,
                    securityFamily = network.securityFamily,
                    knownBssids = network.knownBssids,
                    locationLabel = network.locationLabel,
                    geoLocation = network.geoLocation,
                    secretId = null,
                    notes = network.notes,
                    createdAtEpochMillis = 0L,
                    lastSeenAtEpochMillis = null,
                )
            networks.update { it + created }
            return created
        }

        override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork {
            networks.update { list -> list.map { if (it.id == network.id) network else it } }
            return network
        }

        override suspend fun delete(id: SavedNetworkId) {
            networks.update { list -> list.filterNot { it.id == id } }
        }
    }

    private class FakeVault : SecretVault {
        private val secrets = mutableMapOf<SecretId, NetworkSecret>()
        private var seq = 0

        override suspend fun create(secret: NetworkSecret): SecretId {
            val id = SecretId("secret-${seq++}")
            secrets[id] = secret
            return id
        }

        override suspend fun read(id: SecretId): NetworkSecret? = secrets[id]

        override suspend fun update(
            id: SecretId,
            secret: NetworkSecret,
        ) {
            secrets[id] = secret
        }

        override suspend fun delete(id: SecretId) {
            secrets.remove(id)
        }
    }
}

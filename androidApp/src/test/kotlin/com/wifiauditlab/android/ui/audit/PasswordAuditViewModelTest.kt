package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.audit.PasswordSearchOutcomeKind
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
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
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
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
            assertEquals(PasswordAuditScreenPhase.Invalid, vm.state.value.phase)
        }

    @Test
    fun standardPresetIsDefaultOneMinuteAutomaticPlan() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            val state = vm.state.value
            assertFalse(state.loadingPlan)
            assertNotNull(state.plan)
            assertEquals("Configuración automática", state.explanation?.headline)
            assertTrue(state.explanation!!.details.any { it.contains("procesos de búsqueda") })
            assertEquals(PasswordAuditBudgetPreset.Standard, state.preset)
            assertEquals(PasswordAuditInteractionMode.Automatic, state.mode)
            assertFalse(state.advancedExpanded)
            assertEquals(PasswordAuditScreenPhase.Ready, state.phase)
        }

    @Test
    fun wpa3EligibleProducesPlan() =
        runTest {
            val vm =
                viewModel(
                    request =
                        eligibleRequest(
                            family = SecurityFamily.WPA3_PERSONAL,
                            keyManagements = setOf("SAE"),
                        ),
                )
            assertNotNull(vm.state.value.plan)
            assertNull(vm.state.value.planNotApplicableReason)
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
            assertEquals("Falta la contraseña conocida.", vm.state.value.startBlockedReason)
            vm.onStartAuditClicked()
            assertEquals("Introduce o selecciona la contraseña conocida.", vm.state.value.passwordError)
            assertEquals(SearchState.Idle, vm.state.value.searchState)
        }

    @Test
    fun saveToVaultDefaultsOff() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            assertFalse(vm.state.value.saveToVault)
        }

    @Test
    fun vaultSourceDoesNotPopulatePasswordField() =
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
            assertEquals(PasswordAuditSecretSource.Vault, vm.state.value.secretSource)
            assertEquals("", vm.state.value.passwordInput)
            assertNull(vm.state.value.startBlockedReason)
        }

    @Test
    fun vaultSourceFindsPasswordWithoutShowingIt() =
        runTest {
            val created =
                CreateSavedNetwork(repo, vault)(
                    NewSavedWifiNetwork(
                        alias = "Casa",
                        ssid = "HOME_WIFI",
                        securityFamily = SecurityFamily.WPA2_PERSONAL,
                        knownBssids = setOf(Bssid.of("11:22:33:44:55:66")),
                    ),
                    NetworkSecret("42"),
                )
            val vm = viewModel(request = eligibleRequest().copy(savedNetworkId = created.id))
            vm.onCustomDurationChanged("30")
            vm.onCustomAttemptsChanged("20000")
            vm.applyCustomBudget()
            advanceUntilIdle()
            assertEquals("", vm.state.value.passwordInput)
            vm.onStartAuditClicked()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Found, vm.state.value.outcome)
            assertEquals("", vm.state.value.passwordInput)
        }

    @Test
    fun manualSecretEnablesStart() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.onPasswordChanged("secret")
            assertNull(vm.state.value.startBlockedReason)
            assertEquals(PasswordAuditSecretSource.Manual, vm.state.value.secretSource)
        }

    @Test
    fun resetAutomaticCollapsesAdvanced() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.selectMode(PasswordAuditInteractionMode.Advanced)
            vm.selectPreset(PasswordAuditBudgetPreset.Custom)
            assertTrue(vm.state.value.advancedExpanded)
            vm.resetToAutomaticDefaults()
            assertEquals(PasswordAuditInteractionMode.Automatic, vm.state.value.mode)
            assertEquals(PasswordAuditBudgetPreset.Standard, vm.state.value.preset)
            assertFalse(vm.state.value.advancedExpanded)
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
            assertTrue(vm.state.value.resultReport!!.searchOutcome is PasswordSearchOutcomeKind.Found)
        }

    @Test
    fun stopCancelsActiveAudit() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
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
    fun stopFreezesAttemptGrowthAfterCancel() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.selectPreset(PasswordAuditBudgetPreset.Deep)
            advanceUntilIdle()
            vm.onPasswordChanged("zzzzzzzz")
            vm.onStartAuditClicked()
            advanceUntilIdle()
            if (!vm.state.value.isActive) return@runTest
            vm.stop()
            advanceUntilIdle()
            val attemptsAfterCancel = vm.state.value.metrics?.attempts
            advanceUntilIdle()
            assertEquals(attemptsAfterCancel, vm.state.value.metrics?.attempts)
            assertTrue(
                vm.state.value.outcome == SearchOutcome.Cancelled ||
                    vm.state.value.searchState == SearchState.Cancelled,
            )
        }

    @Test
    fun secondStartWhileActiveIsIgnored() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.selectPreset(PasswordAuditBudgetPreset.Deep)
            advanceUntilIdle()
            vm.onPasswordChanged("zzzzzzzz")
            vm.onStartAuditClicked()
            val firstState = vm.state.value.searchState
            vm.onPasswordChanged("should-not-apply")
            vm.onStartAuditClicked()
            // Password edits and second start are ignored while active.
            assertTrue(vm.state.value.isActive || vm.state.value.outcome != null)
            assertTrue(
                firstState == SearchState.Preparing ||
                    firstState == SearchState.Running ||
                    firstState == SearchState.Cancelling ||
                    vm.state.value.outcome != null,
            )
            if (vm.state.value.isActive) {
                vm.stop()
                advanceUntilIdle()
            }
        }

    @Test
    fun attemptLimitProducesLimitReached() =
        runTest {
            val vm = viewModel(request = eligibleRequest())
            vm.onCustomDurationChanged("30")
            vm.onCustomAttemptsChanged("5")
            vm.applyCustomBudget()
            advanceUntilIdle()
            vm.onPasswordChanged("zzzzzzzz")
            vm.onStartAuditClicked()
            advanceUntilIdle()
            assertTrue(
                vm.state.value.outcome == SearchOutcome.LimitReached ||
                    vm.state.value.outcome == SearchOutcome.NotFound ||
                    vm.state.value.outcome == SearchOutcome.Cancelled,
            )
            assertFalse(vm.state.value.isActive)
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
            assertEquals(PasswordAuditScreenPhase.Invalid, vm.state.value.phase)
        }

    @Test
    fun connectionLostBlocksStart() =
        runTest {
            val observation = sampleObservation()
            val vm =
                viewModel(
                    request = eligibleRequest().copy(observation = observation),
                    eligibility = { PasswordAuditEligibility.NotCurrentlyConnected },
                )
            vm.onPasswordChanged("secret")
            vm.onStartAuditClicked()
            advanceUntilIdle()
            assertNotNull(vm.state.value.connectionLostMessage)
            assertEquals(SearchState.Idle, vm.state.value.searchState)
        }

    private fun viewModel(
        store: PasswordAuditTargetStore = PasswordAuditTargetStore().also { it.set(eligibleRequest()) },
        request: PasswordAuditRequest? = null,
        eligibility: (suspend (WifiObservation) -> PasswordAuditEligibility)? = null,
    ): PasswordAuditViewModel {
        val target =
            if (request != null) {
                PasswordAuditTargetStore().also { it.set(request) }
            } else {
                store
            }
        val checker =
            eligibility?.let { block ->
                object : PasswordAuditEligibilityChecker {
                    override suspend fun check(observation: WifiObservation): PasswordAuditEligibility =
                        block(observation)
                }
            }
        return PasswordAuditViewModel(
            targetStore = target,
            planner = DefaultAutomaticPasswordAuditPlanner(),
            getSavedNetwork = GetSavedNetwork(repo),
            revealSecret = RevealSavedNetworkSecret(repo, vault),
            updateSecret = UpdateSavedNetworkSecret(repo, vault),
            createSavedNetwork = CreateSavedNetwork(repo, vault),
            eligibilityChecker = checker,
            assessNetworkSecurity = AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            engine = DefaultLabSearchEngine(),
            calibration = null,
            strengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
            availableProcessors = 4,
            ioDispatcher = dispatcher,
            uiStrings = spanishUiStrings,
        )
    }

    private val spanishUiStrings =
        UiStrings { id, _ ->
            when (id) {
                R.string.audit_err_missing_password -> "Introduce o selecciona la contraseña conocida."
                R.string.audit_err_password_required -> "Falta la contraseña conocida."
                R.string.audit_err_no_valid_plan -> "No hay un plan automático válido para esta red."
                R.string.audit_err_invalid_config -> "La configuración actual no es válida para iniciar."
                R.string.audit_err_not_connected ->
                    "No estás conectado a esta red. Conéctate primero para realizar una auditoría local."
                R.string.audit_err_connection_lost -> "Se ha perdido la conexión a esta red."
                R.string.audit_err_no_longer_eligible -> "Esta red ya no es elegible para una auditoría de contraseña."
                R.string.audit_err_password_unavailable -> "No se pudo obtener la contraseña conocida."
                R.string.audit_err_vault_save_failed -> "La auditoría continúa; no se pudo guardar en el Vault."
                R.string.audit_err_audit_failed -> "La auditoría se detuvo por un error."
                R.string.audit_err_budget_required -> "Define al menos una duración o un límite de intentos."
                R.string.audit_err_no_plan -> "Sin plan automático."
                R.string.security_family_open -> "Abierta (Open)"
                R.string.security_family_wpa2 -> "WPA2-Personal"
                R.string.security_family_wpa3 -> "WPA3-Personal"
                R.string.security_family_wpa2_wpa3 -> "WPA2/WPA3-Personal (transición)"
                R.string.nearby_band_2_4 -> "2,4 GHz"
                R.string.nearby_band_5 -> "5 GHz"
                else -> error("Missing Spanish test string for resource id=$id")
            }
        }

    private fun eligibleRequest(
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        keyManagements: Set<String> = setOf("WPA_PSK"),
    ): PasswordAuditRequest =
        PasswordAuditRequest(
            network =
                PasswordAuditNetworkContext(
                    displayName = "Casa",
                    ssid = Ssid("HOME_WIFI"),
                    bssid = Bssid.of("11:22:33:44:55:66"),
                    securityProfile =
                        WifiSecurityProfile(
                            family = family,
                            keyManagements = keyManagements,
                            managementFrameProtection = ManagementFrameProtection.CAPABLE,
                            isTransitionMode = false,
                            rawCapabilities = null,
                        ),
                    wifiStandard = null,
                    band = null,
                ),
            savedNetworkId = null,
            observation = sampleObservation(family, keyManagements),
        )

    private fun sampleObservation(
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        keyManagements: Set<String> = setOf("WPA_PSK"),
    ): WifiObservation =
        WifiObservation(
            ssid = Ssid("HOME_WIFI"),
            bssid = Bssid.of("11:22:33:44:55:66"),
            signal = WifiSignal(-50),
            channel = WifiChannel(36, WifiBand.GHZ_5, 5180),
            standard = WifiStandard.WIFI_5,
            securityProfile =
                WifiSecurityProfile(
                    family = family,
                    keyManagements = keyManagements,
                    managementFrameProtection = ManagementFrameProtection.CAPABLE,
                    isTransitionMode = false,
                    rawCapabilities = null,
                ),
            observedAtEpochMillis = 0L,
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

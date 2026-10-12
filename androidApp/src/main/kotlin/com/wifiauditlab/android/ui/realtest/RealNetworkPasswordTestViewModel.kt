package com.wifiauditlab.android.ui.realtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.real.DefaultRealNetworkPasswordEligibilityChecker
import com.wifiauditlab.assessment.domain.real.RealNetworkConsent
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordEligibility
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordEligibilityChecker
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionPort
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionRequest
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult
import com.wifiauditlab.assessment.domain.real.RealNetworkUnavailableReason
import com.wifiauditlab.assessment.domain.real.SensitiveCredential
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orquesta la prueba real de contraseña.
 *
 * No contiene lógica Android de conexión ni Search Engine.
 * Punto de arranque localizable: [startRealNetworkTest].
 */
class RealNetworkPasswordTestViewModel(
    private val targetStore: RealNetworkPasswordTestTargetStore,
    private val executionPort: RealNetworkPasswordExecutionPort,
    private val eligibilityChecker: RealNetworkPasswordEligibilityChecker,
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val revealSecret: RevealSavedNetworkSecret? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(RealNetworkPasswordTestUiState())
    val state: StateFlow<RealNetworkPasswordTestUiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var savedNetworkId: SavedNetworkId? = null

    init {
        val request = targetStore.request.value
        if (request == null) {
            _state.update { it.copy(missingTarget = true) }
        } else {
            savedNetworkId = request.savedNetworkId
            _state.update {
                it.copy(
                    network = request.network,
                    mayUseVaultSecret = request.mayUseVaultSecret,
                    missingTarget = false,
                )
            }
            refreshEligibility()
        }
    }

    fun refreshEligibility() {
        val network = _state.value.network ?: return
        viewModelScope.launch {
            applyEligibility(
                eligibilityChecker.check(
                    ssid = network.ssid,
                    bssid = network.bssid,
                    securityFamily = network.securityFamily,
                ),
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(passwordInput = value) }
    }

    fun togglePasswordVisible() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun setConsentChecked(checked: Boolean) {
        _state.update { it.copy(consentChecked = checked) }
    }

    fun selectSecretSource(source: RealNetworkPasswordSecretSource) {
        _state.update {
            it.copy(
                secretSource = source,
                vaultSecretAuthorized = false,
                passwordInput =
                    if (source == RealNetworkPasswordSecretSource.Manual) it.passwordInput else "",
            )
        }
    }

    /**
     * Explicit authorization before revealing a Vault secret into the password field.
     * Never auto-fills without this call.
     */
    fun authorizeAndLoadVaultSecret() {
        val id = savedNetworkId ?: return
        val reveal = revealSecret ?: return
        viewModelScope.launch {
            val secret = runCatching { reveal(id) }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    passwordInput = secret,
                    vaultSecretAuthorized = true,
                    secretSource = RealNetworkPasswordSecretSource.Vault,
                )
            }
        }
    }

    /**
     * Arranca la ejecución real vía [RealNetworkPasswordExecutionPort].
     *
     * Revalida Exact contra la conexión actual antes de llamar al puerto.
     * Si el match ya no es Exact, **no** invoca el adapter.
     */
    fun startRealNetworkTest() {
        val snapshot = _state.value
        if (!snapshot.canStart) return
        val network = snapshot.network ?: return
        runJob?.cancel()
        runJob =
            viewModelScope.launch {
                val eligibility =
                    eligibilityChecker.check(
                        ssid = network.ssid,
                        bssid = network.bssid,
                        securityFamily = network.securityFamily,
                    )
                applyEligibility(eligibility)
                // Single source of truth: only Executable may call the port.
                if (eligibility !is RealNetworkPasswordEligibility.Executable) {
                    _state.update {
                        it.copy(
                            phase =
                                RealNetworkPasswordTestPhase.Completed(
                                    RealNetworkPasswordExecutionResult.Unavailable(
                                        RealNetworkUnavailableReason.NetworkNotCurrent,
                                    ),
                                ),
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(phase = RealNetworkPasswordTestPhase.Running) }
                val result =
                    executionPort.execute(
                        RealNetworkPasswordExecutionRequest(
                            network = network,
                            credential = SensitiveCredential.of(snapshot.passwordInput),
                            consent = RealNetworkConsent(granted = true),
                        ),
                    )
                _state.update {
                    it.copy(phase = RealNetworkPasswordTestPhase.Completed(result))
                }
            }
    }

    fun stop() {
        val phase = _state.value.phase
        if (phase !is RealNetworkPasswordTestPhase.Running) return
        _state.update { it.copy(phase = RealNetworkPasswordTestPhase.Stopping) }
        executionPort.cancel()
        runJob?.cancel()
        _state.update {
            it.copy(
                phase =
                    RealNetworkPasswordTestPhase.Completed(
                        RealNetworkPasswordExecutionResult.Cancelled,
                    ),
            )
        }
    }

    private fun applyEligibility(eligibility: RealNetworkPasswordEligibility) {
        when (eligibility) {
            is RealNetworkPasswordEligibility.Executable ->
                _state.update {
                    it.copy(
                        networkEligible = true,
                        bssidConfirmed = eligibility.bssidConfirmed,
                    )
                }
            // Legacy: checker maps same-SSID to Executable; if this appears, allow start.
            is RealNetworkPasswordEligibility.ProbableMatchOnly ->
                _state.update {
                    it.copy(networkEligible = true, bssidConfirmed = false)
                }
            else ->
                _state.update {
                    it.copy(networkEligible = false, bssidConfirmed = false)
                }
        }
    }

    override fun onCleared() {
        executionPort.cancel()
        super.onCleared()
    }
}

/** Factory helper for tests without full Koin. */
fun realNetworkEligibilityChecker(
    connectionProvider: CurrentWifiConnectionProvider,
): RealNetworkPasswordEligibilityChecker =
    DefaultRealNetworkPasswordEligibilityChecker(connectionProvider)

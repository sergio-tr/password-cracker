package com.wifiauditlab.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.SecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.supportsSharedPasswordAudit
import com.wifiauditlab.assessment.domain.audit.unsupportedAuditReason
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudget
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.audit.PasswordAuditContext
import com.wifiauditlab.lab.domain.audit.PasswordAuditPerformanceProfile
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlanResult
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Quick Audit UI: password entry + automatic plan preview.
 * Does not run the search engine (execution = follow-up PR).
 * Never feeds the password or strength analysis into the planner.
 */
class PasswordAuditViewModel(
    private val targetStore: PasswordAuditTargetStore,
    private val planner: AutomaticPasswordAuditPlanner,
    private val getSavedNetwork: GetSavedNetwork,
    private val revealSecret: RevealSavedNetworkSecret,
    private val calibration: SearchCalibrationService? = null,
    private val strengthAnalyzer: SecretStrengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordAuditUiState())
    val state: StateFlow<PasswordAuditUiState> = _state.asStateFlow()

    private var calibratedThroughput: Double? = null
    private var request: PasswordAuditRequest? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        val current = targetStore.current
        if (current == null) {
            _state.value = PasswordAuditUiState(missingTarget = true, loadingPlan = false)
            return
        }
        request = current
        _state.value =
            PasswordAuditUiState(
                displayName = current.network.displayName,
                ssidLabel = current.network.ssid.toString(),
                familyLabel = current.network.familyDisplayLabel(),
                metaLine = current.network.metaLine(),
                savedNetworkId = current.savedNetworkId,
                loadingPlan = true,
            )
        viewModelScope.launch(ioDispatcher) {
            val vaultAvailable =
                current.savedNetworkId?.let { id ->
                    getSavedNetwork(id)?.hasSecret == true
                } == true
            _state.update { it.copy(vaultSecretAvailable = vaultAvailable) }
            calibratedThroughput = calibration?.lastRecord()?.measuredAttemptsPerSecond
            rebuildPlan()
        }
    }

    fun onPasswordChanged(value: String) {
        _state.update {
            it.copy(
                passwordInput = value,
                passwordFromVault = false,
                passwordError = null,
                startAcknowledged = false,
                infoMessage = null,
                strength = if (value.isNotEmpty()) strengthAnalyzer.analyze(value) else null,
            )
        }
        refreshStartGate()
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun clearPassword() {
        _state.update {
            it.copy(
                passwordInput = "",
                passwordFromVault = false,
                passwordVisible = false,
                passwordError = null,
                strength = null,
                startAcknowledged = false,
                infoMessage = null,
            )
        }
        refreshStartGate()
    }

    fun useVaultPassword() {
        val id = _state.value.savedNetworkId ?: return
        viewModelScope.launch(ioDispatcher) {
            val plaintext = revealSecret(id)
            if (plaintext.isNullOrEmpty()) {
                _state.update {
                    it.copy(passwordError = "No se pudo leer la contraseña del Vault.")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    passwordInput = plaintext,
                    passwordFromVault = true,
                    passwordError = null,
                    strength = strengthAnalyzer.analyze(plaintext),
                    startAcknowledged = false,
                    infoMessage = null,
                )
            }
            refreshStartGate()
        }
    }

    fun selectPreset(preset: PasswordAuditBudgetPreset) {
        _state.update {
            it.copy(
                preset = preset,
                advancedExpanded = preset == PasswordAuditBudgetPreset.Custom || it.advancedExpanded,
                startAcknowledged = false,
                infoMessage = null,
            )
        }
        rebuildPlan()
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        _state.update { it.copy(advancedExpanded = expanded) }
    }

    fun onCustomDurationChanged(value: String) {
        _state.update { it.copy(customDurationSeconds = value.filter { ch -> ch.isDigit() }) }
    }

    fun onCustomAttemptsChanged(value: String) {
        _state.update { it.copy(customMaxAttempts = value.filter { ch -> ch.isDigit() }) }
    }

    fun applyCustomBudget() {
        _state.update { it.copy(preset = PasswordAuditBudgetPreset.Custom) }
        rebuildPlan()
    }

    /**
     * Validates password + plan readiness. Does not start the engine (PR5).
     */
    fun onStartAuditClicked() {
        val password = _state.value.passwordInput
        if (password.isEmpty()) {
            _state.update {
                it.copy(passwordError = "Introduce o recupera la contraseña conocida.")
            }
            return
        }
        if (_state.value.plan == null) {
            _state.update {
                it.copy(startBlockedReason = "No hay un plan automático válido para esta red.")
            }
            return
        }
        _state.update {
            it.copy(
                passwordError = null,
                startAcknowledged = true,
                infoMessage =
                    "Plan listo. La ejecución local con DETENER llega en la siguiente actualización.",
            )
        }
    }

    private fun rebuildPlan() {
        val req = request ?: return
        _state.update { it.copy(loadingPlan = true, planNotApplicableReason = null) }
        viewModelScope.launch(ioDispatcher) {
            val budget =
                budgetFromState(_state.value) ?: run {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = null,
                            explanation = null,
                            planNotApplicableReason =
                                "Define al menos una duración o un límite de intentos.",
                        )
                    }
                    refreshStartGate()
                    return@launch
                }
            val family = req.network.securityProfile.family
            val context =
                PasswordAuditContext(
                    sharedPasswordApplicable = family.supportsSharedPasswordAudit(),
                    inapplicableReason =
                        if (family.supportsSharedPasswordAudit()) {
                            null
                        } else {
                            family.unsupportedAuditReason()
                        },
                )
            val performance =
                PasswordAuditPerformanceProfile(
                    calibratedAttemptsPerSecond = calibratedThroughput,
                    availableProcessors = availableProcessors,
                )
            when (val result = planner.createPlan(context, performance, budget)) {
                is PasswordAuditPlanResult.Ready -> {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = result.plan,
                            explanation = result.plan.explanation,
                            feasibilityRating = result.plan.feasibility.rating,
                            feasibilityReason = result.plan.feasibility.reason,
                            planNotApplicableReason = null,
                        )
                    }
                }
                is PasswordAuditPlanResult.NotApplicable -> {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = null,
                            explanation = null,
                            feasibilityRating = null,
                            feasibilityReason = null,
                            planNotApplicableReason = result.reason,
                        )
                    }
                }
            }
            refreshStartGate()
        }
    }

    private fun budgetFromState(ui: PasswordAuditUiState): PasswordAuditBudget? =
        when (ui.preset) {
            PasswordAuditBudgetPreset.Quick -> PasswordAuditBudget.quick()
            PasswordAuditBudgetPreset.Standard -> PasswordAuditBudget.standard()
            PasswordAuditBudgetPreset.Deep -> PasswordAuditBudget.deep()
            PasswordAuditBudgetPreset.Custom -> {
                val duration =
                    ui.customDurationSeconds.toLongOrNull()?.takeIf { it > 0 }?.seconds
                val attempts =
                    ui.customMaxAttempts.toLongOrNull()?.takeIf { it > 0 }?.let { CombinationCount.of(it) }
                if (duration == null && attempts == null) {
                    null
                } else {
                    PasswordAuditBudget(
                        maxDuration = duration,
                        maxAttempts = attempts,
                        preset = PasswordAuditBudgetPreset.Custom,
                    )
                }
            }
        }

    private fun refreshStartGate() {
        _state.update { current ->
            val reason =
                when {
                    current.passwordInput.isEmpty() -> "Falta la contraseña conocida."
                    current.plan == null -> current.planNotApplicableReason ?: "Sin plan automático."
                    else -> null
                }
            current.copy(startBlockedReason = reason)
        }
    }

    override fun onCleared() {
        clearPassword()
        super.onCleared()
    }
}

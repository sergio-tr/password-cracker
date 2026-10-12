package com.wifiauditlab.android.wifi

import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackend
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackendResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgress
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditUnavailableReason

/**
 * Android-side frontier for real Wi‑Fi audit execution.
 *
 * Orchestration, strategy, plan, UI and Search Engine planning live above this
 * class. Experimental platform verification must stay behind the single
 * marked implementation point inside [execute].
 */
class AndroidRealWifiAuditBackend : RealWifiAuditBackend {
    @Volatile
    private var cancelled: Boolean = false

    override fun cancel() {
        cancelled = true
    }

    override suspend fun execute(
        plan: RealWifiAuditPlan,
        progress: RealWifiAuditProgressSink,
    ): RealWifiAuditBackendResult {
        cancelled = false
        progress.onProgress(
            RealWifiAuditProgress(
                strategyLabel = plan.strategyLabel,
                candidatesProcessed = 0,
                elapsedMillis = 0,
                workerCount = plan.limits.workerCountHint,
                statusMessage = "Prepared",
            ),
        )

        /*
         * ============================================================
         * USER_IMPLEMENTATION_POINT_REAL_WIFI_AUDIT
         * ============================================================
         *
         * Backend específico de ejecución sobre la red real.
         *
         * En este punto ya están resueltos:
         *
         * - red seleccionada por el usuario (plan.network);
         * - SecurityFamily (plan.securityProfile.family);
         * - WifiSecurityProfile (plan.securityProfile);
         * - estrategia elegida (plan.strategy);
         * - AuditPlan (plan);
         * - límites (plan.limits);
         * - métricas / progress sink;
         * - cancelación ([cancel] / [cancelled]);
         * - lifecycle;
         * - UI;
         * - navegación;
         *
         * Disponible aquí:
         *   plan.network.ssid / bssid? / securityFamily / displayName
         *   plan.securityProfile
         *   plan.strategy
         *   plan.limits
         *   plan.strategyLabel
         *
         * Cualquier backend experimental dependiente del mecanismo
         * de autenticación debe implementarse únicamente detrás
         * de esta frontera.
         *
         * No introducir lógica Android/red dentro de shared:lab.
         * No automatizar intentos de credenciales contra el AP aquí
         * hasta que exista un diseño revisado.
         *
         * Search Engine V2 candidate generation is NOT connected yet.
         */

        if (cancelled) {
            return RealWifiAuditBackendResult.Cancelled
        }

        return RealWifiAuditBackendResult.Unavailable(
            RealWifiAuditUnavailableReason.NotImplemented,
        )
    }
}

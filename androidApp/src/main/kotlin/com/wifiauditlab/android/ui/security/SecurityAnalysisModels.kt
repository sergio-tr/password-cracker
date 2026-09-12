package com.wifiauditlab.android.ui.security

import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/** Payload handed from Network Detail to the dedicated analysis screen. */
data class SecurityAnalysisRequest(
    val displayName: String,
    val ssidLabel: String,
    val profile: WifiSecurityProfile,
)

/** Process-scoped holder so navigation does not serialize secrets or large graphs. */
class SecurityAnalysisTargetStore {
    @Volatile
    var current: SecurityAnalysisRequest? = null
        private set

    fun set(request: SecurityAnalysisRequest) {
        current = request
    }

    fun clear() {
        current = null
    }
}

data class AuthenticationSummary(
    val familyLabel: String,
    val modeLabel: String,
    val encryptionLabel: String,
    val pmfLabel: String?,
    val transitionLabel: String?,
)

data class SecurityAnalysisUiState(
    val displayName: String = "",
    val ssidLabel: String = "",
    val loading: Boolean = true,
    val missingTarget: Boolean = false,
    val assessment: SecurityAssessment? = null,
    val authentication: AuthenticationSummary? = null,
    val recommendations: List<String> = emptyList(),
    val technicalExpanded: Boolean = false,
)

fun ratingLabel(rating: SecurityRating): String =
    when (rating) {
        SecurityRating.HIGH -> "Seguridad alta"
        SecurityRating.MODERATE -> "Seguridad moderada"
        SecurityRating.LOW -> "Seguridad baja"
        SecurityRating.INSECURE -> "Insegura"
    }

fun familyLabel(family: SecurityFamily): String =
    when (family) {
        SecurityFamily.OPEN -> "Abierta (Open)"
        SecurityFamily.WEP -> "WEP"
        SecurityFamily.WPA_PERSONAL -> "WPA-Personal"
        SecurityFamily.WPA2_PERSONAL -> "WPA2-Personal"
        SecurityFamily.WPA3_PERSONAL -> "WPA3-Personal"
        SecurityFamily.WPA2_WPA3_PERSONAL -> "WPA2/WPA3-Personal (transición)"
        SecurityFamily.WPA2_ENTERPRISE -> "WPA2-Enterprise"
        SecurityFamily.WPA3_ENTERPRISE -> "WPA3-Enterprise"
        SecurityFamily.OWE -> "OWE (Enhanced Open)"
        SecurityFamily.PASSPOINT -> "Passpoint"
        SecurityFamily.DPP -> "DPP (Easy Connect)"
        SecurityFamily.UNKNOWN -> "Desconocida"
    }

fun buildAuthenticationSummary(profile: WifiSecurityProfile): AuthenticationSummary {
    val mode =
        when (profile.family) {
            SecurityFamily.WPA2_ENTERPRISE, SecurityFamily.WPA3_ENTERPRISE, SecurityFamily.PASSPOINT ->
                "Modo enterprise (802.1X)"
            SecurityFamily.OPEN, SecurityFamily.OWE -> "Sin contraseña compartida"
            SecurityFamily.UNKNOWN -> "Modo no determinado"
            else -> "Modo personal (PSK)"
        }
    val encryption =
        when {
            profile.keyManagements.isNotEmpty() -> profile.keyManagements.sorted().joinToString(", ")
            profile.family == SecurityFamily.OPEN -> "Sin cifrado de enlace"
            profile.family == SecurityFamily.WEP -> "WEP (obsoleto)"
            else -> profile.rawCapabilities ?: "No informado por el sistema"
        }
    val pmf =
        when (profile.managementFrameProtection) {
            ManagementFrameProtection.REQUIRED -> "PMF/MFP obligatorio"
            ManagementFrameProtection.CAPABLE -> "PMF/MFP opcional (capable)"
            ManagementFrameProtection.DISABLED -> "PMF/MFP desactivado"
            ManagementFrameProtection.UNKNOWN -> null
        }
    val transition =
        when {
            profile.isTransitionMode || profile.family == SecurityFamily.WPA2_WPA3_PERSONAL ->
                "Transición WPA2/WPA3: la protección real depende del cliente que se conecte."
            else -> null
        }
    return AuthenticationSummary(
        familyLabel = familyLabel(profile.family),
        modeLabel = mode,
        encryptionLabel = encryption,
        pmfLabel = pmf,
        transitionLabel = transition,
    )
}

/**
 * Defensive configuration tips only — never attack steps, password generation,
 * candidates, or lab linkage.
 */
fun defensiveRecommendations(
    profile: WifiSecurityProfile,
    assessment: SecurityAssessment,
): List<String> {
    val tips = linkedSetOf<String>()
    when (profile.family) {
        SecurityFamily.OPEN -> {
            tips += "No introduzcas contraseñas ni datos bancarios en una red abierta."
            tips += "Si controlas el router, activa WPA3 (o al menos WPA2) con una frase larga."
        }
        SecurityFamily.WEP, SecurityFamily.WPA_PERSONAL -> {
            tips += "Actualiza el router a WPA2 o WPA3; este mecanismo ya no se considera seguro."
            tips += "Cambia la contraseña de administración del router tras actualizar el cifrado."
        }
        SecurityFamily.WPA2_PERSONAL -> {
            tips += "Usa una frase de acceso larga y única; evita palabras del diccionario."
            tips += "Si el router lo permite, activa WPA3 o el modo de transición WPA2/WPA3."
            if (profile.managementFrameProtection == ManagementFrameProtection.DISABLED) {
                tips += "Activa la protección de tramas de gestión (PMF) en la configuración Wi‑Fi."
            }
        }
        SecurityFamily.WPA2_WPA3_PERSONAL -> {
            tips += "Prefiere clientes compatibles con WPA3 para aprovechar la protección moderna."
            tips += "Cuando todos los dispositivos lo permitan, desactiva el modo solo WPA2."
        }
        SecurityFamily.WPA3_PERSONAL -> {
            tips += "Mantén el firmware del router al día."
            tips += "Conserva una frase de acceso larga aunque WPA3 sea más resistente."
        }
        SecurityFamily.WPA2_ENTERPRISE, SecurityFamily.WPA3_ENTERPRISE, SecurityFamily.PASSPOINT -> {
            tips += "Confía solo en perfiles corporativos desplegados por tu organización."
            tips += "No ignores avisos de certificado al conectarte."
        }
        SecurityFamily.OWE -> {
            tips += "El tráfico va cifrado, pero cualquiera puede unirse: sigue evitando datos sensibles."
        }
        SecurityFamily.DPP -> {
            tips += "Usa el flujo Easy Connect oficial del fabricante para añadir dispositivos."
        }
        SecurityFamily.UNKNOWN -> {
            tips += "Si es tu red, revisa el panel del router para confirmar el tipo de seguridad."
        }
    }
    if (assessment.rating == SecurityRating.INSECURE || assessment.rating == SecurityRating.LOW) {
        tips += "Prioriza otra red de confianza mientras no puedas mejorar la configuración."
    }
    return tips.toList()
}

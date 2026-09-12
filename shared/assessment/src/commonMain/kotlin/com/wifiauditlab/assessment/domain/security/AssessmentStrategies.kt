package com.wifiauditlab.assessment.domain.security

import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

private fun WifiSecurityProfile.pmfFinding(): SecurityFinding? =
    when (managementFrameProtection) {
        ManagementFrameProtection.REQUIRED ->
            SecurityFinding(
                Severity.INFO,
                "Protección de tramas activa",
                "La red exige protección de tramas de gestión (PMF), lo que dificulta ataques de desconexión.",
            )
        ManagementFrameProtection.DISABLED ->
            SecurityFinding(
                Severity.LOW,
                "Sin protección de tramas",
                "La red no protege las tramas de gestión, por lo que es más fácil forzar desconexiones.",
            )
        else -> null
    }

class OpenNetworkAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = profile.family == SecurityFamily.OPEN

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        SecurityAssessment(
            rating = SecurityRating.INSECURE,
            headline = "Red abierta",
            plainExplanation =
                "Cualquiera puede conectarse y el tráfico viaja sin cifrar. Evita introducir " +
                    "información sensible en esta red.",
            technicalSummary = "Sin autenticación ni cifrado de enlace (open system).",
            findings =
                listOf(
                    SecurityFinding(
                        Severity.CRITICAL,
                        "Tráfico sin cifrar",
                        "Los datos pueden ser interceptados por cualquier equipo dentro del alcance.",
                    ),
                ),
        )
}

class LegacyNetworkAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean =
        profile.family == SecurityFamily.WEP || profile.family == SecurityFamily.WPA_PERSONAL

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment {
        val wep = profile.family == SecurityFamily.WEP
        return SecurityAssessment(
            rating = SecurityRating.INSECURE,
            headline = if (wep) "Cifrado WEP obsoleto" else "WPA (versión 1) obsoleto",
            plainExplanation =
                "Este mecanismo está roto desde hace años y puede vulnerarse con herramientas " +
                    "comunes. No debería usarse.",
            technicalSummary =
                if (wep) {
                    "WEP: RC4 con IV corto, recuperable."
                } else {
                    "WPA1/TKIP: vulnerable a ataques conocidos."
                },
            findings =
                listOf(
                    SecurityFinding(
                        Severity.CRITICAL,
                        "Mecanismo obsoleto",
                        "Actualiza el router a WPA2 o WPA3 para proteger la red.",
                    ),
                ),
        )
    }
}

class PersonalNetworkAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = profile.family in PERSONAL

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment {
        val (rating, headline, plain) =
            when (profile.family) {
                SecurityFamily.WPA3_PERSONAL ->
                    Triple(
                        SecurityRating.HIGH,
                        "WPA3-Personal",
                        "Cifrado moderno y robusto para redes domésticas. Buena elección.",
                    )
                SecurityFamily.WPA2_WPA3_PERSONAL ->
                    Triple(
                        SecurityRating.HIGH,
                        "WPA2/WPA3-Personal (transición)",
                        "Admite WPA3 y mantiene WPA2 por compatibilidad. La seguridad efectiva depende del " +
                            "dispositivo que se conecte.",
                    )
                else ->
                    Triple(
                        SecurityRating.MODERATE,
                        "WPA2-Personal",
                        "Cifrado ampliamente usado y aceptable. WPA3 sería más resistente frente a ataques offline.",
                    )
            }
        val findings =
            buildList {
                profile.pmfFinding()?.let { add(it) }
                if (profile.family == SecurityFamily.WPA2_PERSONAL) {
                    add(
                        SecurityFinding(
                            Severity.MEDIUM,
                            "Vulnerable a ataques offline",
                            "Una contraseña débil puede recuperarse capturando el handshake. Usa una frase larga.",
                        ),
                    )
                }
            }
        return SecurityAssessment(rating, headline, plain, "Key management: ${profile.keyManagements}", findings)
    }

    private companion object {
        val PERSONAL =
            setOf(
                SecurityFamily.WPA2_PERSONAL,
                SecurityFamily.WPA3_PERSONAL,
                SecurityFamily.WPA2_WPA3_PERSONAL,
            )
    }
}

class EnterpriseNetworkAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean =
        profile.family == SecurityFamily.WPA2_ENTERPRISE || profile.family == SecurityFamily.WPA3_ENTERPRISE

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment {
        val wpa3 = profile.family == SecurityFamily.WPA3_ENTERPRISE
        return SecurityAssessment(
            rating = SecurityRating.HIGH,
            headline = if (wpa3) "WPA3-Enterprise" else "WPA2-Enterprise",
            plainExplanation =
                "Autenticación individual mediante servidor (802.1X). Habitual en empresas y " +
                    "universidades. No se protege con una única contraseña compartida.",
            technicalSummary = "802.1X/EAP" + if (wpa3) ", Suite-B/PMF" else "",
            findings =
                listOf(
                    SecurityFinding(
                        Severity.INFO,
                        "Autenticación por usuario",
                        "Las credenciales son individuales; no se pueden guardar como una simple contraseña de red.",
                    ),
                ),
        )
    }
}

class OweAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = profile.family == SecurityFamily.OWE

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        SecurityAssessment(
            rating = SecurityRating.MODERATE,
            headline = "Open enhanced (OWE)",
            plainExplanation =
                "Red sin contraseña pero con el tráfico cifrado de forma individual. Mejor que una " +
                    "red abierta clásica, aunque no autentica al punto de acceso.",
            technicalSummary = "Opportunistic Wireless Encryption (RFC 8110).",
            findings =
                listOf(
                    SecurityFinding(
                        Severity.LOW,
                        "Sin autenticación del punto de acceso",
                        "No garantiza que te conectas al router legítimo; sigue siendo posible la suplantación.",
                    ),
                ),
        )
}

class PasspointAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = profile.family == SecurityFamily.PASSPOINT

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        SecurityAssessment(
            rating = SecurityRating.HIGH,
            headline = "Passpoint (Hotspot 2.0)",
            plainExplanation =
                "Conexión automática y cifrada a redes de operadores mediante credenciales " +
                    "aprovisionadas. Seguridad de nivel empresarial.",
            technicalSummary = "Hotspot 2.0 sobre 802.1X.",
            findings = emptyList(),
        )
}

class DppAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = profile.family == SecurityFamily.DPP

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        SecurityAssessment(
            rating = SecurityRating.HIGH,
            headline = "Wi-Fi Easy Connect (DPP)",
            plainExplanation =
                "Aprovisionamiento moderno mediante claves públicas (por ejemplo, códigos QR), sin " +
                    "compartir una contraseña.",
            technicalSummary = "Device Provisioning Protocol.",
            findings = emptyList(),
        )
}

class UnsupportedAssessmentStrategy : SecurityAssessmentStrategy {
    override fun supports(profile: WifiSecurityProfile): Boolean = true

    override suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        SecurityAssessment(
            rating = SecurityRating.LOW,
            headline = "Configuración no reconocida",
            plainExplanation = "No se ha podido identificar con certeza el mecanismo de seguridad de esta red.",
            technicalSummary = "Capabilities: ${profile.rawCapabilities ?: "desconocidas"}",
            findings =
                listOf(
                    SecurityFinding(
                        Severity.MEDIUM,
                        "Evaluación no concluyente",
                        "Trata la red con precaución hasta confirmar su configuración.",
                    ),
                ),
        )
}

package com.wifiauditlab.android.ui.onboarding

enum class OnboardingPage(
    val title: String,
    val body: String,
) {
    Nearby(
        title = "Analiza tu entorno Wi‑Fi",
        body =
            "Descubre redes visibles, entiende su seguridad y reconoce las que " +
                "ya hayas guardado. Solo se leen metadatos que Android expone.",
    ),
    Vault(
        title = "Guarda tus redes de forma segura",
        body =
            "Asigna un alias y una ubicación, y guarda credenciales en el Vault " +
                "cifrado. Nunca se almacenan en texto plano.",
    ),
    Lab(
        title = "Experimenta en un laboratorio seguro",
        body =
            "El laboratorio es sintético: no prueba contraseñas contra redes reales. " +
                "Toda búsqueda tiene límites y un botón STOP siempre visible.",
    ),
}

data class OnboardingUiState(
    val pageIndex: Int = 0,
    val completed: Boolean = false,
) {
    val page: OnboardingPage get() = OnboardingPage.entries[pageIndex.coerceIn(0, OnboardingPage.entries.lastIndex)]
    val isLast: Boolean get() = pageIndex >= OnboardingPage.entries.lastIndex
}

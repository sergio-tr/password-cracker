package com.wifiauditlab.android.ui.onboarding

import androidx.annotation.StringRes
import com.wifiauditlab.android.R

enum class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    Nearby(
        titleRes = R.string.onboarding_nearby_title,
        bodyRes = R.string.onboarding_nearby_body,
    ),
    Vault(
        titleRes = R.string.onboarding_vault_title,
        bodyRes = R.string.onboarding_vault_body,
    ),
    Lab(
        titleRes = R.string.onboarding_lab_title,
        bodyRes = R.string.onboarding_lab_body,
    ),
}

data class OnboardingUiState(
    val pageIndex: Int = 0,
    val completed: Boolean = false,
) {
    val page: OnboardingPage get() = OnboardingPage.entries[pageIndex.coerceIn(0, OnboardingPage.entries.lastIndex)]
    val isLast: Boolean get() = pageIndex >= OnboardingPage.entries.lastIndex
}

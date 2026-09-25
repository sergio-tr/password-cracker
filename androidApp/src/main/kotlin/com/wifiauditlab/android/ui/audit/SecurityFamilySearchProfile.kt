package com.wifiauditlab.android.ui.audit

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.lab.domain.audit.SharedPasswordSearchProfile

/** Maps assessment security family to blind lab search profile (no assessment import in lab). */
fun SecurityFamily.toSharedPasswordSearchProfile(): SharedPasswordSearchProfile? =
    when (this) {
        SecurityFamily.WPA_PERSONAL -> SharedPasswordSearchProfile.WPA_PERSONAL_PSK
        SecurityFamily.WPA2_PERSONAL -> SharedPasswordSearchProfile.WPA2_PERSONAL_PSK
        SecurityFamily.WPA3_PERSONAL -> SharedPasswordSearchProfile.WPA3_PERSONAL_PSK
        SecurityFamily.WPA2_WPA3_PERSONAL -> SharedPasswordSearchProfile.WPA2_WPA3_TRANSITION_PSK
        SecurityFamily.WEP -> SharedPasswordSearchProfile.WEP_HEX
        else -> null
    }

package com.wifiauditlab.core.audit

/** Why shared-password audit does not apply to a network family. */
enum class PasswordAuditInapplicableReason {
    OpenNetwork,
    Owe,
    Enterprise,
    Passpoint,
    Dpp,
    Wep,
    UnknownFamily,
    UnsupportedAuth,
}

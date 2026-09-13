package com.wifiauditlab.android.i18n

/**
 * Per-app language preference tags.
 * SYSTEM follows device locales (empty [androidx.appcompat.app.AppCompatDelegate] list).
 */
enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

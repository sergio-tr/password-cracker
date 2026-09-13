package com.wifiauditlab.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language via official [AppCompatDelegate] APIs.
 * Empty locale list = follow system language.
 */
object AppLanguagePreferences {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    const val SYSTEM = "system"
    const val SPANISH = "es"
    const val ENGLISH = "en"

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    fun applyStored(context: Context) {
        apply(context, currentTag(context))
    }

    fun apply(
        context: Context,
        tag: String,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .apply()
        val locales =
            when (tag) {
                SPANISH -> LocaleListCompat.forLanguageTags("es")
                ENGLISH -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

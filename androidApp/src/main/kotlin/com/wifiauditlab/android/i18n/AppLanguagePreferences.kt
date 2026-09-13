package com.wifiauditlab.android.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language via official [AppCompatDelegate] APIs.
 * Empty locale list = follow system language.
 *
 * Requires [androidx.appcompat.app.AppCompatActivity] (or installViewFactory)
 * so locales propagate into the Activity configuration used by Compose.
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
        apply(context, currentTag(context), recreate = false)
    }

    fun apply(
        context: Context,
        tag: String,
        recreate: Boolean = true,
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
        if (recreate) {
            context.findActivity()?.recreate()
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}

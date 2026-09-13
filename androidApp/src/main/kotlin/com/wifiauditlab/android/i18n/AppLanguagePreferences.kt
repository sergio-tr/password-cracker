package com.wifiauditlab.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language via official [AppCompatDelegate.setApplicationLocales].
 *
 * Empty locale list = follow system language.
 * Requires [androidx.appcompat.app.AppCompatActivity] so locales propagate into
 * the Activity [android.content.res.Configuration] that Compose [androidx.compose.ui.res.stringResource] reads.
 *
 * Do **not** call [android.app.Activity.recreate] here: AppCompat applies a configuration
 * change through the official API when locales change.
 */
object AppLanguagePreferences {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage =
        AppLanguage.fromTag(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AppLanguage.SYSTEM.tag)
                ?: AppLanguage.SYSTEM.tag,
        )

    /** @deprecated Prefer [current]. */
    fun currentTag(context: Context): String = current(context).tag

    fun applyStored(context: Context) {
        apply(context, current(context))
    }

    fun apply(
        context: Context,
        language: AppLanguage,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.tag)
            .apply()
        val locales =
            when (language) {
                AppLanguage.SPANISH -> LocaleListCompat.forLanguageTags("es")
                AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Convenience overloads matching stored string tags. */
    fun apply(
        context: Context,
        tag: String,
    ) {
        apply(context, AppLanguage.fromTag(tag))
    }
}

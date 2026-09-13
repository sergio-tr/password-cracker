package com.wifiauditlab.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Runtime per-app language: AppCompat locales must change string resolution.
 * Runs on emulator CI (instrumented).
 */
@RunWith(AndroidJUnit4::class)
class AppLanguagePreferencesInstrumentedTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        appContext.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun applyEnglish_resolvesNavNearbyInEnglish() {
        AppLanguagePreferences.apply(appContext, AppLanguagePreferences.ENGLISH, recreate = false)
        assertEquals(AppLanguagePreferences.ENGLISH, AppLanguagePreferences.currentTag(appContext))

        val locales = AppCompatDelegate.getApplicationLocales()
        assertFalseEmpty(locales)
        assertEquals("en", locales[0]?.language)

        val localized =
            appContext.createConfigurationContext(
                android.content.res.Configuration(appContext.resources.configuration).apply {
                    setLocale(Locale.ENGLISH)
                },
            )
        assertEquals("Nearby", localized.getString(R.string.nav_nearby))
        assertEquals("Lab", localized.getString(R.string.nav_lab))
    }

    @Test
    fun applySpanish_resolvesNavNearbyInSpanish() {
        AppLanguagePreferences.apply(appContext, AppLanguagePreferences.SPANISH, recreate = false)
        val localized =
            appContext.createConfigurationContext(
                android.content.res.Configuration(appContext.resources.configuration).apply {
                    setLocale(Locale("es"))
                },
            )
        assertEquals("Cercanas", localized.getString(R.string.nav_nearby))
        assertEquals("Laboratorio", localized.getString(R.string.nav_lab))
    }

    private fun assertFalseEmpty(locales: LocaleListCompat) {
        assertTrue("expected non-empty application locales", !locales.isEmpty)
    }
}

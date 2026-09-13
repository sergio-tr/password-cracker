package com.wifiauditlab.android.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Ensures ES/EN string packs diverge and language preference persistence works.
 * Avoids asserting AppCompatDelegate.getApplicationLocales() immediately after
 * setApplicationLocales (can be empty on some API levels without a resumed Activity).
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
    fun spanishAndEnglishResourcePacks_differForNavLabels() {
        val es = localizedContext(Locale("es"))
        val en = localizedContext(Locale.ENGLISH)
        assertEquals("Cercanas", es.getString(R.string.nav_nearby))
        assertEquals("Nearby", en.getString(R.string.nav_nearby))
        assertEquals("Laboratorio", es.getString(R.string.nav_lab))
        assertEquals("Lab", en.getString(R.string.nav_lab))
        assertNotEquals(es.getString(R.string.lab_title), en.getString(R.string.lab_title))
    }

    @Test
    fun apply_persistsLanguageTag() {
        AppLanguagePreferences.apply(appContext, AppLanguagePreferences.ENGLISH, recreate = false)
        assertEquals(AppLanguagePreferences.ENGLISH, AppLanguagePreferences.currentTag(appContext))

        AppLanguagePreferences.apply(appContext, AppLanguagePreferences.SPANISH, recreate = false)
        assertEquals(AppLanguagePreferences.SPANISH, AppLanguagePreferences.currentTag(appContext))

        AppLanguagePreferences.apply(appContext, AppLanguagePreferences.SYSTEM, recreate = false)
        assertEquals(AppLanguagePreferences.SYSTEM, AppLanguagePreferences.currentTag(appContext))
    }

    private fun localizedContext(locale: Locale): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config)
    }
}

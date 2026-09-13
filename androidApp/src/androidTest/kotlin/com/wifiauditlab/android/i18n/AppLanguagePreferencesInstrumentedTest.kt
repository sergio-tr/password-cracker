package com.wifiauditlab.android.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Pack divergence + preference persistence (no Activity UI).
 * Runtime UI proof lives in [MainActivityRuntimeLocaleTest].
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
    fun apply_persistsLanguageEnum_esEnSystem() {
        AppLanguagePreferences.apply(appContext, AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, AppLanguagePreferences.current(appContext))

        AppLanguagePreferences.apply(appContext, AppLanguage.SPANISH)
        assertEquals(AppLanguage.SPANISH, AppLanguagePreferences.current(appContext))

        AppLanguagePreferences.apply(appContext, AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, AppLanguagePreferences.current(appContext))
    }

    private fun localizedContext(locale: Locale): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun markOnboardingCompletedForSiblingMainActivityTests() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            ctx.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("completed", true)
                .commit()
        }
    }
}

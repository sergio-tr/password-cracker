package com.wifiauditlab.android.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * FIX-01B: smoke regression that key product labels exist in ES and EN resource packs.
 */
@RunWith(AndroidJUnit4::class)
class ProductLocalizationRegressionTest {
    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun spanishPack_exposesMajorScreenTitles() {
        val ctx = localizedContext(Locale("es"))
        assertEquals("Cercanas", ctx.getString(R.string.nav_nearby))
        assertEquals("Laboratorio sintético", ctx.getString(R.string.lab_title))
        assertEquals("Guardadas", ctx.getString(R.string.nav_vault))
        assertEquals("Ajustes", ctx.getString(R.string.settings_title))
        assertEquals("Auditoría Wi-Fi", ctx.getString(R.string.real_audit_title))
    }

    @Test
    fun englishPack_exposesMajorScreenTitles() {
        val ctx = localizedContext(Locale.ENGLISH)
        assertEquals("Nearby", ctx.getString(R.string.nav_nearby))
        assertEquals("Synthetic lab", ctx.getString(R.string.lab_title))
        assertEquals("Saved", ctx.getString(R.string.nav_vault))
        assertEquals("Settings", ctx.getString(R.string.settings_title))
        assertEquals("Wi-Fi audit", ctx.getString(R.string.real_audit_title))
    }

    @Test
    fun realAuditCta_existsInBothLocales() {
        val es = localizedContext(Locale("es"))
        val en = localizedContext(Locale.ENGLISH)
        assertEquals("Auditar esta red", es.getString(R.string.real_audit_cta))
        assertEquals("Audit this network", en.getString(R.string.real_audit_cta))
        assertEquals(
            "Backend de verificación no disponible",
            es.getString(R.string.real_audit_start_blocked_backend),
        )
        assertEquals(
            "Verification backend not available",
            en.getString(R.string.real_audit_start_blocked_backend),
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocale(locale)
        return appContext.createConfigurationContext(configuration)
    }
}

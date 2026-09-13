package com.wifiauditlab.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX-01A: official [AppCompatDelegate.setApplicationLocales] must update Compose
 * [androidx.compose.ui.res.stringResource] on an [androidx.appcompat.app.AppCompatActivity]
 * without app-code [android.app.Activity.recreate].
 *
 * Required transitions: ES → EN, EN → ES, EN → SYSTEM.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityRuntimeLocaleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LocaleProbeActivity>()

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDownLocales() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        appContext.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun spanishToEnglish_updatesLocalizedLabel() {
        applyLanguageAndWait(AppLanguage.SPANISH)
        assertProbeLabel(spanishNearby())

        applyLanguageAndWait(AppLanguage.ENGLISH)
        assertProbeLabel(englishNearby())
    }

    @Test
    fun englishToSpanish_updatesLocalizedLabel() {
        applyLanguageAndWait(AppLanguage.ENGLISH)
        assertProbeLabel(englishNearby())

        applyLanguageAndWait(AppLanguage.SPANISH)
        assertProbeLabel(spanishNearby())
    }

    @Test
    fun englishToSystem_clearsOverride() {
        applyLanguageAndWait(AppLanguage.ENGLISH)
        assertProbeLabel(englishNearby())

        applyLanguageAndWait(AppLanguage.SYSTEM)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val label = activityNavLabel()
            label == englishNearby() || label == spanishNearby()
        }
        assertEquals(AppLanguage.SYSTEM, AppLanguagePreferences.current(appContext))
    }

    private fun applyLanguageAndWait(language: AppLanguage) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppLanguagePreferences.apply(appContext, language)
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            AppLanguagePreferences.current(appContext) == language
        }
        composeRule.waitForIdle()
    }

    private fun assertProbeLabel(expected: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            activityNavLabel() == expected
        }
        composeRule
            .onNodeWithTag(LocaleProbeActivity.TAG_NAV_LABEL)
            .assertIsDisplayed()
            .assertTextEquals(expected)
    }

    private fun activityNavLabel(): String =
        try {
            composeRule.activity.getString(R.string.nav_nearby)
        } catch (_: Throwable) {
            ""
        }

    private fun spanishNearby(): String =
        appContext.createConfigurationContext(
            android.content.res.Configuration(appContext.resources.configuration).apply {
                setLocale(java.util.Locale("es"))
            },
        ).getString(R.string.nav_nearby)

    private fun englishNearby(): String =
        appContext.createConfigurationContext(
            android.content.res.Configuration(appContext.resources.configuration).apply {
                setLocale(java.util.Locale.ENGLISH)
            },
        ).getString(R.string.nav_nearby)
}

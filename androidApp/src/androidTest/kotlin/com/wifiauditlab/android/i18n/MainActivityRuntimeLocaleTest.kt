package com.wifiauditlab.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.MainActivity
import com.wifiauditlab.android.R
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FIX-01A: official [AppCompatDelegate.setApplicationLocales] must update Compose
 * bottom-nav labels on [MainActivity] without app-code [android.app.Activity.recreate].
 *
 * Required transitions: ES → EN, EN → ES, EN → SYSTEM.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityRuntimeLocaleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDownLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        appContext.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun spanishToEnglish_updatesBottomNavLabels() {
        applyLanguageAndWait(AppLanguage.SPANISH)
        waitForNavLabel(spanishNearby())

        applyLanguageAndWait(AppLanguage.ENGLISH)
        waitForNavLabel(englishNearby())
        assertGone(spanishNearby())
    }

    @Test
    fun englishToSpanish_updatesBottomNavLabels() {
        applyLanguageAndWait(AppLanguage.ENGLISH)
        waitForNavLabel(englishNearby())

        applyLanguageAndWait(AppLanguage.SPANISH)
        waitForNavLabel(spanishNearby())
        assertGone(englishNearby())
    }

    @Test
    fun englishToSystem_clearsOverride() {
        applyLanguageAndWait(AppLanguage.ENGLISH)
        waitForNavLabel(englishNearby())

        applyLanguageAndWait(AppLanguage.SYSTEM)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            hasText(englishNearby()) || hasText(spanishNearby())
        }
        assert(AppLanguagePreferences.current(appContext) == AppLanguage.SYSTEM)
    }

    private fun applyLanguageAndWait(language: AppLanguage) {
        composeRule.runOnUiThread {
            // Official API only — no Activity.recreate() in production path.
            AppLanguagePreferences.apply(appContext, language)
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            AppLanguagePreferences.current(appContext) == language
        }
        composeRule.waitForIdle()
    }

    private fun waitForNavLabel(label: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) { hasText(label) }
        composeRule.onNodeWithText(label).assertIsDisplayed()
    }

    private fun assertGone(label: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) { !hasText(label) }
    }

    private fun hasText(label: String): Boolean =
        composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

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

    companion object {
        @JvmStatic
        @BeforeClass
        fun completeOnboardingBeforeActivityLaunch() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            ctx.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("completed", true)
                .commit()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            ctx.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}

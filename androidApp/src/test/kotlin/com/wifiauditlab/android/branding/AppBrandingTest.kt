package com.wifiauditlab.android.branding

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Branding check without Robolectric: parse the versioned string resources.
 * Historical package/applicationId names may still say wifiauditlab.
 */
class AppBrandingTest {
    @Test
    fun appName_isWifiSearcher_inDefaultAndEnglish() {
        val roots =
            listOf(
                File("src/main/res/values/strings.xml"),
                File("src/main/res/values-en/strings.xml"),
                File("androidApp/src/main/res/values/strings.xml"),
                File("androidApp/src/main/res/values-en/strings.xml"),
            )
        val files = roots.filter { it.isFile }
        assertTrue("strings.xml not found from ${File(".").absolutePath}", files.isNotEmpty())
        files.forEach { file ->
            val text = file.readText()
            assertTrue(
                "${file.path} must define WiFiSearcher as app_name",
                text.contains("""<string name="app_name">WiFiSearcher</string>"""),
            )
            assertTrue(
                "${file.path} must not keep Wi-Fi Audit Lab as app_name",
                !text.contains("""<string name="app_name">Wi-Fi Audit Lab</string>"""),
            )
        }
    }
}

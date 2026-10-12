package com.wifiauditlab.lab

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the ADR-003 / module-graph rule: `:shared:lab` commonMain must not
 * depend on Android framework types or `:shared:assessment`.
 */
class LabModuleBoundaryTest {
    @Test
    fun commonMainSources_haveNoAndroidOrAssessmentImports() {
        val commonMain =
            locateCommonMain()
                ?: error("Could not locate shared/lab commonMain sources")
        val violations = mutableListOf<String>()
        commonMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import android.") ||
                        trimmed.startsWith("import androidx.") ||
                        trimmed.contains("com.wifiauditlab.assessment")
                    ) {
                        violations += "${file.name}:${index + 1}: $trimmed"
                    }
                }
            }
        assertTrue(
            violations.isEmpty(),
            "Forbidden imports in shared:lab commonMain:\n${violations.joinToString("\n")}",
        )
    }

    private fun locateCommonMain(): File? {
        val candidates =
            listOf(
                File("src/commonMain/kotlin"),
                File("shared/lab/src/commonMain/kotlin"),
            )
        return candidates.firstOrNull { it.isDirectory }
    }
}

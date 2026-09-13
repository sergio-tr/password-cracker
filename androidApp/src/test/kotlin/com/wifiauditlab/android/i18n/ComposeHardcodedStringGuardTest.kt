package com.wifiauditlab.android.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * FIX-05 static guard: fail CI when new user-facing Compose literals appear in
 * presentation sources instead of [androidx.compose.ui.res.stringResource].
 *
 * Focuses on [androidx.compose.material3.Text] and [contentDescription] string
 * literals — not ViewModel messages or domain code.
 */
class ComposeHardcodedStringGuardTest {
    @Test
    fun presentationComposeSources_haveNoUserFacingHardcodedStrings() {
        val uiRoot = locateUiSourcesRoot()
        val violations = mutableListOf<String>()

        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                scanFile(file.toPath(), violations)
            }

        assertTrue(
            "Hardcoded user-facing Compose strings detected (use stringResource):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun scanFile(
        path: Path,
        violations: MutableList<String>,
    ) {
        val relative = path.fileName.toString()
        val lines = path.readText().lines()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed

            TEXT_LITERAL.findAll(line).forEach { match ->
                val literal = match.groupValues[1]
                if (!isAllowedTextLiteral(literal)) {
                    violations += "$relative:${index + 1}: Text(\"$literal\")"
                }
            }

            CONTENT_DESC_LITERAL.findAll(line).forEach { match ->
                val literal = match.groupValues[1]
                if (!isAllowedContentDescription(literal)) {
                    violations += "$relative:${index + 1}: contentDescription = \"$literal\""
                }
            }
        }
    }

    private fun isAllowedTextLiteral(literal: String): Boolean {
        if (literal in ALLOWED_TEXT_LITERALS) return true
        // Numeric-only or punctuation-only templates (steppers, bullets with dynamic body).
        if (literal.none { it.isLetter() }) return true
        return false
    }

    private fun isAllowedContentDescription(literal: String): Boolean = literal in ALLOWED_CONTENT_DESCRIPTIONS

    private fun locateUiSourcesRoot(): File {
        val moduleRoot =
            sequenceOf(
                File("src/main/kotlin/com/wifiauditlab/android/ui"),
                File("androidApp/src/main/kotlin/com/wifiauditlab/android/ui"),
            ).firstOrNull { it.isDirectory }
                ?: error("Could not locate androidApp UI sources from ${File(".").absolutePath}")
        return moduleRoot
    }

    companion object {
        // Only pure string literals — ignore templates containing `$` interpolation.
        private val TEXT_LITERAL = Regex("""Text\s*\(\s*"([^"$]+)"""")
        private val CONTENT_DESC_LITERAL = Regex("""contentDescription\s*=\s*"([^"$]+)"""")

        /** Legitimate non-translatable UI chrome. Extend sparingly. */
        private val ALLOWED_TEXT_LITERALS =
            setOf(
                "-",
                "+",
            )

        private val ALLOWED_CONTENT_DESCRIPTIONS = emptySet<String>()
    }
}

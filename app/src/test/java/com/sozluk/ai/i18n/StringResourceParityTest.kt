package com.sozluk.ai.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards translation completeness (spec §8): every string/plural key defined in the base locale
 * must exist in ru and tk (and vice versa), so no screen falls back to English in RU/TK mode.
 */
class StringResourceParityTest {

    private fun resFile(qualifier: String): File {
        val relative = "src/main/res/$qualifier/strings.xml"
        // Unit tests run with the module dir as CWD; fall back to the repo root layout just in case.
        return listOf(File(relative), File("app/$relative")).first { it.exists() }
    }

    private val keyRegex = Regex("""<(?:string|plurals)\s+name="([^"]+)"""")

    private fun keysOf(qualifier: String): Set<String> =
        keyRegex.findAll(resFile(qualifier).readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun ruAndTk_coverExactlySameKeysAsBase() {
        val base = keysOf("values")
        val ru = keysOf("values-ru")
        val tk = keysOf("values-tk")

        assertTrue("base locale should not be empty", base.isNotEmpty())
        assertEquals("RU missing/extra keys vs base", base, ru)
        assertEquals("TK missing/extra keys vs base", base, tk)
    }

    @Test
    fun russianPluralHasAllRequiredForms() {
        val ruText = resFile("values-ru").readText()
        val pluralBlock = Regex("""<plurals name="home_words_due">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .find(ruText)?.groupValues?.get(1)
            ?: error("home_words_due plural missing in values-ru")
        listOf("one", "few", "many", "other").forEach { form ->
            assertTrue("RU plural must define quantity=$form", pluralBlock.contains("""quantity="$form""""))
        }
    }
}

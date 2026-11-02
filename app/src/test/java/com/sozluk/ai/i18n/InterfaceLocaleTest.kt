package com.sozluk.ai.i18n

import com.sozluk.ai.domain.i18n.interfaceLangFromTag
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceLocaleTest {

    @Test
    fun supportedLanguages_mapToThemselves() {
        assertEquals("en", interfaceLangFromTag("en"))
        assertEquals("ru", interfaceLangFromTag("ru"))
        assertEquals("tk", interfaceLangFromTag("tk"))
    }

    @Test
    fun regionAndScriptSubtags_areStripped() {
        assertEquals("tk", interfaceLangFromTag("tk-TM"))
        assertEquals("ru", interfaceLangFromTag("ru_RU"))
        assertEquals("en", interfaceLangFromTag("en-US"))
    }

    @Test
    fun caseIsNormalized() {
        assertEquals("ru", interfaceLangFromTag("RU"))
        assertEquals("tk", interfaceLangFromTag("Tk-Latn-TM"))
    }

    @Test
    fun unsupportedOrMissing_fallsBackToEnglish() {
        assertEquals("en", interfaceLangFromTag("de"))
        assertEquals("en", interfaceLangFromTag(""))
        assertEquals("en", interfaceLangFromTag(null))
        assertEquals("en", interfaceLangFromTag("tr")) // Turkish must not be treated as Turkmen
    }
}

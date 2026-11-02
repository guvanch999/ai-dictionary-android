package com.sozluk.ai.image

import com.sozluk.ai.domain.image.OcrHeuristics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrHeuristicsTest {

    @Test
    fun blankOrEmpty_isGarbage() {
        assertTrue(OcrHeuristics.isGarbage(""))
        assertTrue(OcrHeuristics.isGarbage("   "))
    }

    @Test
    fun punctuationOnly_isGarbage() {
        assertTrue(OcrHeuristics.isGarbage("... !!! -"))
    }

    @Test
    fun singleLetters_isGarbage() {
        assertTrue(OcrHeuristics.isGarbage("a"))
        assertTrue(OcrHeuristics.isGarbage("I a"))
    }

    @Test
    fun digitsOnly_isGarbage() {
        // "12" has no letters, so no real word.
        assertTrue(OcrHeuristics.isGarbage("12 34"))
    }

    @Test
    fun realWord_isNotGarbage() {
        assertFalse(OcrHeuristics.isGarbage("hello"))
        assertFalse(OcrHeuristics.isGarbage("a bank"))
        assertFalse(OcrHeuristics.isGarbage("!!! ok !!!"))
    }
}

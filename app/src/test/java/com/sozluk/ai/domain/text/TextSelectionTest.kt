package com.sozluk.ai.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSelectionTest {

    @Test
    fun singleWord_countIsOne() {
        assertEquals(1, TextSelection.wordCount("hello"))
        assertEquals(listOf("hello"), TextSelection.tokenize("hello"))
    }

    @Test
    fun twoWords_countIsTwo() {
        assertEquals(2, TextSelection.wordCount("hello world"))
        assertEquals(listOf("hello", "world"), TextSelection.tokenize("hello world"))
    }

    @Test
    fun sentence_countsWordsOnly() {
        assertEquals(4, TextSelection.wordCount("The quick brown fox."))
        assertEquals(listOf("The", "quick", "brown", "fox"), TextSelection.tokenize("The quick brown fox."))
    }

    @Test
    fun punctuationStripped_butInnerMarksKept() {
        assertEquals(listOf("Hello", "world"), TextSelection.tokenize("Hello, world!"))
        assertEquals(listOf("don't"), TextSelection.tokenize("don't"))
        assertEquals(listOf("well-known"), TextSelection.tokenize("well-known"))
        assertEquals(listOf("quote"), TextSelection.tokenize("\"quote\""))
    }

    @Test
    fun multipleSpaces_collapse() {
        assertEquals(2, TextSelection.wordCount("  spaced   out  "))
        assertEquals(listOf("spaced", "out"), TextSelection.tokenize("  spaced   out  "))
    }

    @Test
    fun blankOrPunctuationOnly_isEmpty() {
        assertEquals(0, TextSelection.wordCount(""))
        assertEquals(0, TextSelection.wordCount("   "))
        assertEquals(0, TextSelection.wordCount("!!! --- ..."))
        assertEquals(emptyList<String>(), TextSelection.tokenize("!!!"))
    }
}

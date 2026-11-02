package com.sozluk.ai.quiz

import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.generators.AudioChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.ExampleGapGenerator
import com.sozluk.ai.domain.quiz.generators.LetterScrambleGenerator
import com.sozluk.ai.domain.quiz.generators.ReverseChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.TranslationChoiceGenerator
import com.sozluk.ai.support.word
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQuizGeneratorsTest {

    private val ru = NativeLang.RU
    private val rnd = Random(1)

    // Five same-part-of-speech words with comparable lengths → valid distractors for choice types.
    private val cat = word("cat", term = "cat", ru = "кошка", pos = "noun")
    private val dog = word("dog", term = "dog", ru = "собака", pos = "noun")
    private val rat = word("rat", term = "rat", ru = "крыса", pos = "noun")
    private val bat = word("bat", term = "bat", ru = "мышь", pos = "noun")
    private val hen = word("hen", term = "hen", ru = "птица", pos = "noun")
    private val pool = listOf(cat, dog, rat, bat, hen)

    private fun assertValidChoice(payload: LocalQuizPayload?, expectedAnswer: String) {
        assertNotNull(payload)
        val p = payload!!
        assertEquals(4, p.options.size)
        assertEquals(4, p.options.distinct().size) // no duplicate options
        assertEquals(1, p.options.count { it == expectedAnswer }) // exactly one correct
        assertEquals(expectedAnswer, p.answerText)
        assertEquals(expectedAnswer, p.options[p.answerIndex])
    }

    @Test
    fun translationChoice_isValidFourWayChoice() {
        val p = TranslationChoiceGenerator().generate(cat, pool, ru, rnd)
        assertValidChoice(p, "кошка")
        assertEquals("cat", p!!.prompt)
    }

    @Test
    fun reverseChoice_optionsAreTerms() {
        val p = ReverseChoiceGenerator().generate(cat, pool, ru, rnd)
        assertValidChoice(p, "cat")
        assertEquals("кошка", p!!.prompt)
        assertTrue(p.options.all { opt -> pool.any { it.term == opt } })
    }

    @Test
    fun audioChoice_carriesTtsTermAndWordOptions() {
        val p = AudioChoiceGenerator().generate(cat, pool, ru, rnd)
        assertValidChoice(p, "cat")
        assertEquals("cat", p!!.ttsText)
        assertEquals("", p.prompt)
    }

    @Test
    fun distractors_neverIncludeSynonyms() {
        val target = word("cat", term = "cat", ru = "кошка", pos = "noun", synonyms = listOf("dog"))
        repeat(50) { seed ->
            val p = ReverseChoiceGenerator().generate(target, pool, ru, Random(seed.toLong()))!!
            assertFalse("dog is a synonym and must not be a distractor", "dog" in p.options)
        }
    }

    @Test
    fun choiceGenerators_returnNullWhenPoolTooSmall() {
        val tiny = listOf(cat, dog, rat) // only two possible distractors
        assertNull(TranslationChoiceGenerator().generate(cat, tiny, ru, rnd))
        assertNull(ReverseChoiceGenerator().generate(cat, tiny, ru, rnd))
    }

    @Test
    fun distractors_relaxLengthThenPartOfSpeech() {
        val target = word("cat", term = "cat", pos = "noun")
        // Same POS but far longer than ±40% → only usable once the length filter is relaxed.
        val longNouns = listOf(
            target,
            word("elephant", term = "elephant", pos = "noun"),
            word("crocodile", term = "crocodile", pos = "noun"),
            word("butterfly", term = "butterfly", pos = "noun"),
        )
        assertNotNull(ReverseChoiceGenerator().generate(target, longNouns, ru, rnd))

        // Different POS and length → only usable once BOTH filters are relaxed.
        val verbs = listOf(
            target,
            word("running", term = "running", pos = "verb"),
            word("jumping", term = "jumping", pos = "verb"),
            word("swimming", term = "swimming", pos = "verb"),
        )
        assertNotNull(ReverseChoiceGenerator().generate(target, verbs, ru, rnd))
    }

    @Test
    fun letterScramble_producesADifferentArrangement() {
        val p = LetterScrambleGenerator().generate(cat.copy(term = "hello"), pool, ru, rnd)
        assertNotNull(p)
        assertEquals("hello", p!!.answerText)
        assertFalse(p.prompt == "hello")
        assertEquals("hello".toSortedSet(), p.prompt.toSortedSet())
        assertTrue(p.options.isEmpty())
    }

    @Test
    fun letterScramble_skipsShortWords() {
        assertNull(LetterScrambleGenerator().generate(cat.copy(term = "cat"), pool, ru, rnd))
    }

    @Test
    fun letterScramble_skipsWordsThatCannotBeReordered() {
        assertNull(LetterScrambleGenerator().generate(cat.copy(term = "aaaa"), pool, ru, rnd))
    }

    @Test
    fun exampleGap_blanksOutTheTerm() {
        val target = cat.copy(
            examples = listOf(Example(en = "The cat sat on the mat", ru = "", tk = "")),
        )
        val p = ExampleGapGenerator().generate(target, pool, ru, rnd)
        assertValidChoice(p, "cat")
        assertTrue("_____" in p!!.prompt)
        assertFalse("cat" in p.prompt)
    }

    @Test
    fun exampleGap_returnsNullWhenNoExampleContainsTheTerm() {
        val target = cat.copy(
            examples = listOf(Example(en = "Dogs run fast", ru = "", tk = "")),
        )
        assertNull(ExampleGapGenerator().generate(target, pool, ru, rnd))
    }
}

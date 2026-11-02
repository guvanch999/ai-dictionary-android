package com.sozluk.ai.quiz

import com.sozluk.ai.data.quiz.AiQuizSourceImpl
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.ai.GeneratedQuiz
import com.sozluk.ai.domain.ai.QuizGenRequest
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.generators.AudioChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.ExampleGapGenerator
import com.sozluk.ai.domain.quiz.generators.LetterScrambleGenerator
import com.sozluk.ai.domain.quiz.generators.ReverseChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.TranslationChoiceGenerator
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.support.word
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiQuizSourceImplTest {

    private val generators: List<LocalQuizGenerator> = listOf(
        TranslationChoiceGenerator(),
        ReverseChoiceGenerator(),
        LetterScrambleGenerator(),
        AudioChoiceGenerator(),
        ExampleGapGenerator(),
    )

    private val words = listOf(
        word("w1", term = "apple", pos = "noun"),
        word("w2", term = "bread", pos = "noun"),
        word("w3", term = "chair", pos = "noun"),
        word("w4", term = "drink", pos = "noun"),
        word("w5", term = "eagle", pos = "noun"),
        word("w6", term = "flame", pos = "noun"),
    )

    private fun sourceReturning(vararg generated: GeneratedQuiz): AiQuizSourceImpl {
        val client = mockk<AiClient>()
        coEvery { client.generateQuizzes(any()) } returns Result.success(generated.toList())
        val repo = mockk<WordRepository>()
        coEvery { repo.getAll() } returns words
        return AiQuizSourceImpl(client, generators, repo, InterfaceLocaleProvider { "en" }, Random(7))
    }

    private fun payloadOf(item: QuizItem): LocalQuizPayload =
        quizJson.decodeFromString(LocalQuizPayload.serializer(), item.payloadJson)

    private val aiTypes = setOf(
        QuizType.AI_GAP_FILL, QuizType.AI_DEFINITION, QuizType.AI_SYNONYM,
        QuizType.AI_FIND_MISTAKE, QuizType.AI_DIALOG,
    )

    private fun validQuiz(wordId: String) = GeneratedQuiz(
        type = "gap_fill",
        wordId = wordId,
        question = "I want an ___.",
        options = listOf("apple", "bread", "chair", "drink"),
        answerIndex = 0,
        explanation = "because",
    )

    @Test
    fun validQuiz_becomesAiQuizItem_withMappedTypeAndPayload() = runTest {
        val source = sourceReturning(validQuiz("w1"))
        val items = source.generate(words, words.size, NativeLang.RU)

        val w1 = items.first { it.wordId == "w1" }
        assertEquals(QuizType.AI_GAP_FILL, w1.type)
        val payload = payloadOf(w1)
        assertEquals("I want an ___.", payload.prompt)
        assertEquals(listOf("apple", "bread", "chair", "drink"), payload.options)
        assertEquals(0, payload.answerIndex)
        assertEquals("apple", payload.answerText)
        assertEquals("because", payload.explanation)
    }

    @Test
    fun items_carryBlankIdAndSessionId() = runTest {
        val source = sourceReturning(validQuiz("w1"))
        val items = source.generate(words, words.size, NativeLang.RU)
        assertTrue(items.all { it.id.isEmpty() && it.sessionId.isEmpty() })
    }

    @Test
    fun outOfBoundsAnswerIndex_isReplacedByLocalItem() = runTest {
        val broken = validQuiz("w1").copy(answerIndex = 7)
        val source = sourceReturning(broken)
        val items = source.generate(words, words.size, NativeLang.RU)

        val w1 = items.firstOrNull { it.wordId == "w1" }
        assertNotNull(w1)
        assertFalse("w1 should fall back to a local type", w1!!.type in aiTypes)
    }

    @Test
    fun duplicateOptions_isReplacedByLocalItem() = runTest {
        val broken = validQuiz("w1").copy(options = listOf("apple", "apple", "chair", "drink"))
        val source = sourceReturning(broken)
        val items = source.generate(words, words.size, NativeLang.RU)

        val w1 = items.first { it.wordId == "w1" }
        assertFalse(w1.type in aiTypes)
    }

    @Test
    fun unknownWordId_isDiscarded_wordGetsLocalItem() = runTest {
        val stray = validQuiz("does-not-exist")
        val source = sourceReturning(stray)
        val items = source.generate(words, words.size, NativeLang.RU)

        // No AI items survive; every returned item is a local type for a real word.
        assertTrue(items.none { it.type in aiTypes })
        assertTrue(items.all { it.wordId in words.map { w -> w.id } })
    }

    @Test
    fun aiClientFailure_returnsEmptyList() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.generateQuizzes(any()) } returns Result.failure(AiError.Network)
        val repo = mockk<WordRepository>()
        coEvery { repo.getAll() } returns words
        val source = AiQuizSourceImpl(client, generators, repo, InterfaceLocaleProvider { "en" }, Random(7))

        assertEquals(emptyList<QuizItem>(), source.generate(words, words.size, NativeLang.RU))
    }

    @Test
    fun uiLang_comesFromInterfaceLang_independentOfNativeLang() = runTest {
        // Interface language (en) must reach the prompt even when the native language is RU (spec §8.2).
        val client = mockk<AiClient>()
        val request = slot<QuizGenRequest>()
        coEvery { client.generateQuizzes(capture(request)) } returns Result.success(emptyList())
        val repo = mockk<WordRepository>()
        coEvery { repo.getAll() } returns words
        val source = AiQuizSourceImpl(client, generators, repo, InterfaceLocaleProvider { "en" }, Random(7))

        source.generate(words, words.size, NativeLang.RU)

        assertEquals("en", request.captured.uiLang)
    }
}

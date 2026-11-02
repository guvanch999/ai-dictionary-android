package com.sozluk.ai.quiz

import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.AiQuizSource
import com.sozluk.ai.domain.quiz.ExamComposer
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.quiz.generators.AudioChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.ExampleGapGenerator
import com.sozluk.ai.domain.quiz.generators.LetterScrambleGenerator
import com.sozluk.ai.domain.quiz.generators.ReverseChoiceGenerator
import com.sozluk.ai.domain.quiz.generators.TranslationChoiceGenerator
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.srs.ExamScope
import com.sozluk.ai.domain.srs.WordSelector
import com.sozluk.ai.support.word
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.random.Random
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamComposerTest {

    private val localTypes = setOf(
        QuizType.TRANSLATION_CHOICE,
        QuizType.REVERSE_CHOICE,
        QuizType.LETTER_SCRAMBLE,
        QuizType.AUDIO_CHOICE,
        QuizType.EXAMPLE_GAP,
    )

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

    @Test
    fun composesAndPersistsAnInProgressLocalOnlySession() = runTest {
        val wordRepo = mockk<WordRepository>()
        coEvery { wordRepo.getAll() } returns words
        val settings = mockk<SettingsRepository>()
        every { settings.nativeLang } returns flowOf(NativeLang.RU)

        val examRepo = mockk<ExamRepository>(relaxed = true)
        val sessionSlot = slot<ExamSession>()
        val itemsSlot = slot<List<QuizItem>>()
        coEvery { examRepo.createSession(capture(sessionSlot)) } just Runs
        coEvery { examRepo.upsertItems(capture(itemsSlot)) } just Runs

        var counter = 0
        val composer = ExamComposer(
            wordSelector = WordSelector(Random(5)),
            generators = generators,
            wordRepository = wordRepo,
            examRepository = examRepo,
            settingsRepository = settings,
            aiSource = null,
            random = Random(5),
            now = { 1_000L },
            idGen = { "id${counter++}" },
        )

        val session = composer.compose(
            count = 4,
            scope = ExamScope.ALL,
            includeMastered = true,
            mode = "practice",
        )

        coVerify(exactly = 1) { examRepo.createSession(any()) }
        coVerify(exactly = 1) { examRepo.upsertItems(any()) }

        val items = itemsSlot.captured
        assertEquals(SessionState.IN_PROGRESS, session.state)
        assertEquals(1_000L, session.createdAt)
        // Exam size is measured in questions: at most `count` items, wordIds derived from them.
        assertTrue(items.size <= 4)
        assertTrue(session.wordIds.size <= 4)
        assertEquals(session.wordIds.toSet().size, session.wordIds.size) // distinct
        assertEquals(items.size, session.total)
        assertTrue(items.isNotEmpty())

        // Every item belongs to this session and is a local quiz type.
        assertTrue(items.all { it.sessionId == session.id })
        assertTrue(items.all { it.type in localTypes })
        assertTrue(items.all { it.wordId in session.wordIds })

        // At most two items per word, and those two are of different types.
        items.groupBy { it.wordId }.forEach { (_, group) ->
            assertTrue(group.size in 1..2)
            assertEquals(group.size, group.map { it.type }.toSet().size)
        }
    }

    @Test
    fun isDeterministicForAFixedSeed() = runTest {
        suspend fun run(): List<QuizItem> {
            val wordRepo = mockk<WordRepository>()
            coEvery { wordRepo.getAll() } returns words
            val settings = mockk<SettingsRepository>()
            every { settings.nativeLang } returns flowOf(NativeLang.RU)
            val examRepo = mockk<ExamRepository>(relaxed = true)
            val itemsSlot = slot<List<QuizItem>>()
            coEvery { examRepo.upsertItems(capture(itemsSlot)) } just Runs
            var counter = 0
            ExamComposer(
                wordSelector = WordSelector(Random(11)),
                generators = generators,
                wordRepository = wordRepo,
                examRepository = examRepo,
                settingsRepository = settings,
                random = Random(11),
                now = { 1_000L },
                idGen = { "id${counter++}" },
            ).compose(4, ExamScope.ALL, includeMastered = true, mode = "practice")
            return itemsSlot.captured
        }

        val a = run().map { it.wordId to it.type }
        val b = run().map { it.wordId to it.type }
        assertEquals(a, b)
    }

    private fun aiPayload(): String = quizJson.encodeToString(
        LocalQuizPayload.serializer(),
        LocalQuizPayload(prompt = "q", options = listOf("a", "b", "c", "d"), answerIndex = 0, answerText = "a"),
    )

    private suspend fun composeWith(aiSource: AiQuizSource?, count: Int): List<QuizItem> {
        val wordRepo = mockk<WordRepository>()
        coEvery { wordRepo.getAll() } returns words
        val settings = mockk<SettingsRepository>()
        every { settings.nativeLang } returns flowOf(NativeLang.RU)
        val examRepo = mockk<ExamRepository>(relaxed = true)
        val itemsSlot = slot<List<QuizItem>>()
        coEvery { examRepo.upsertItems(capture(itemsSlot)) } just Runs
        var counter = 0
        ExamComposer(
            wordSelector = WordSelector(Random(5)),
            generators = generators,
            wordRepository = wordRepo,
            examRepository = examRepo,
            settingsRepository = settings,
            aiSource = aiSource,
            random = Random(5),
            now = { 1_000L },
            idGen = { "id${counter++}" },
        ).compose(count, ExamScope.ALL, includeMastered = true, mode = "practice")
        return itemsSlot.captured
    }

    @Test
    fun online_mixesInAtMostTwoOpenSentenceItems() = runTest {
        // AI batch produced AI_ items -> "online" -> open items are mixed in.
        val aiSource = mockk<AiQuizSource>()
        coEvery { aiSource.generate(any(), any(), any()) } answers {
            firstArg<List<WordEntry>>().map {
                QuizItem(id = "", sessionId = "", wordId = it.id, type = QuizType.AI_GAP_FILL, payloadJson = aiPayload())
            }
        }

        val items = composeWith(aiSource, count = 8)
        val open = items.filter { it.type == QuizType.OPEN_SENTENCE }
        assertTrue(open.isNotEmpty())
        assertTrue(open.size <= 2)
        // Per-word cap still holds with open items reserved.
        items.groupBy { it.wordId }.forEach { (_, group) -> assertTrue(group.size in 1..2) }
    }

    @Test
    fun offline_noOpenSentenceItems() = runTest {
        // AI batch produced nothing -> "offline" -> no open items (they can't be graded).
        val aiSource = mockk<AiQuizSource>()
        coEvery { aiSource.generate(any(), any(), any()) } returns emptyList()

        val items = composeWith(aiSource, count = 8)
        assertTrue(items.none { it.type == QuizType.OPEN_SENTENCE })
    }
}

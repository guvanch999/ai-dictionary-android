package com.sozluk.ai.domain.quiz

import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.srs.ExamScope
import com.sozluk.ai.domain.srs.WordSelector
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Assembles an exam session (spec §7): picks words via [WordSelector], builds local quiz items
 * (each word gets at most two items of different types), optionally mixes in AI items via
 * [aiSource], shuffles the order and persists everything as an IN_PROGRESS session so it survives
 * app restarts. With no [aiSource] the exam is 100% local and works offline.
 */
class ExamComposer(
    private val wordSelector: WordSelector,
    private val generators: List<LocalQuizGenerator>,
    private val wordRepository: WordRepository,
    private val examRepository: ExamRepository,
    private val settingsRepository: SettingsRepository,
    private val aiSource: AiQuizSource? = null,
    private val random: Random = Random.Default,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun compose(
        count: Int,
        scope: ExamScope,
        includeMastered: Boolean,
        mode: String,
    ): ExamSession {
        val pool = wordRepository.getAll()
        val at = now()
        val selected = wordSelector.select(pool, count, scope, includeMastered, at)
        val nativeLang = settingsRepository.nativeLang.first()
        val sessionId = idGen()

        // AI items are a task-08 concern; the seam merges them per word toward the ~40/60 ratio.
        val aiItems = (aiSource?.generate(selected, selected.size, nativeLang) ?: emptyList())
            .map { it.copy(id = idGen(), sessionId = sessionId) }
        val aiCountByWord = aiItems.groupingBy { it.wordId }.eachCount()

        // Open-answer items (F-4) are graded live via the AI, so mix them in only when we know we're
        // online — inferred from the AI batch actually producing AI_ items (which also proves the key
        // works). Cap at MAX_OPEN_ITEMS per exam, distinct words, reserving each word's slot below.
        val online = aiItems.any { it.type.name.startsWith("AI_") }
        val openItems = if (online) buildOpenItems(selected, sessionId) else emptyList()
        val openCountByWord = openItems.groupingBy { it.wordId }.eachCount()
        val reservedByWord = (aiCountByWord.keys + openCountByWord.keys).associateWith {
            (aiCountByWord[it] ?: 0) + (openCountByWord[it] ?: 0)
        }

        val localItems = buildLocalItems(selected, pool, nativeLang, sessionId, reservedByWord)
        // Exam size is measured in QUESTIONS (spec §133): cap the assembled items to `count`.
        // `take` of a shuffled subset preserves the per-word invariants (<=2 items, distinct types).
        // Open items are reserved first so they reliably appear, then the rest fills the remaining slots.
        val reservedOpen = openItems.take(count)
        val rest = (aiItems + localItems).shuffled(random).take((count - reservedOpen.size).coerceAtLeast(0))
        val items = (reservedOpen + rest).shuffled(random)
        val usedWordIds = items.map { it.wordId }.distinct()

        val session = ExamSession(
            id = sessionId,
            createdAt = at,
            wordIds = usedWordIds,
            mode = mode,
            total = items.size,
            state = SessionState.IN_PROGRESS,
        )
        examRepository.createSession(session)
        examRepository.upsertItems(items)
        return session
    }

    private fun buildLocalItems(
        selected: List<WordEntry>,
        pool: List<WordEntry>,
        nativeLang: com.sozluk.ai.domain.model.NativeLang,
        sessionId: String,
        aiCountByWord: Map<String, Int>,
    ): List<QuizItem> {
        val items = mutableListOf<QuizItem>()
        selected.forEachIndexed { index, word ->
            val slots = (MAX_ITEMS_PER_WORD - (aiCountByWord[word.id] ?: 0)).coerceAtLeast(0)
            if (slots == 0) return@forEachIndexed
            // Rotate the generator order per word so quiz types spread across the exam. Each
            // generator has a distinct type, so items on one word are always of different types.
            var made = 0
            for (generator in generators.rotate(index)) {
                if (made >= slots) break
                val payload = generator.generate(word, pool, nativeLang, random) ?: continue
                items += QuizItem(
                    id = idGen(),
                    sessionId = sessionId,
                    wordId = word.id,
                    type = generator.type,
                    payloadJson = quizJson.encodeToString(LocalQuizPayload.serializer(), payload),
                )
                made++
            }
        }
        return items
    }

    /** Up to [MAX_OPEN_ITEMS] OPEN_SENTENCE items (F-4), one per distinct word. Payload carries just
     *  the term in [LocalQuizPayload.answerText]; the UI renders the localized instruction from it. */
    private fun buildOpenItems(selected: List<WordEntry>, sessionId: String): List<QuizItem> =
        selected.take(MAX_OPEN_ITEMS).map { word ->
            QuizItem(
                id = idGen(),
                sessionId = sessionId,
                wordId = word.id,
                type = QuizType.OPEN_SENTENCE,
                payloadJson = quizJson.encodeToString(
                    LocalQuizPayload.serializer(),
                    LocalQuizPayload(prompt = "", answerText = word.term),
                ),
            )
        }

    private fun <T> List<T>.rotate(n: Int): List<T> {
        if (isEmpty()) return this
        val k = ((n % size) + size) % size
        return drop(k) + take(k)
    }

    private companion object {
        const val MAX_ITEMS_PER_WORD = 2
        const val MAX_OPEN_ITEMS = 2
    }
}

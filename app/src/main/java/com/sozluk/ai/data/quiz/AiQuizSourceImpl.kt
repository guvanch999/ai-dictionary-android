package com.sozluk.ai.data.quiz

import com.sozluk.ai.data.ai.AiQuizTypeMapping
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.GeneratedQuiz
import com.sozluk.ai.domain.ai.QuizGenRequest
import com.sozluk.ai.domain.ai.QuizWord
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.AiQuizSource
import com.sozluk.ai.domain.quiz.LocalQuizGenerator
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.repository.WordRepository
import kotlin.random.Random

/**
 * Turns the selected exam words into AI quiz items (spec §6.2, §7.1). Requests one AI question per
 * word (with [ExamComposer][com.sozluk.ai.domain.quiz.ExamComposer]'s MAX_ITEMS_PER_WORD of 2, that
 * leaves one local slot per word for the composer to fill — roughly the 40/60 local/AI target).
 *
 * Every returned [GeneratedQuiz] is validated on the client; a broken one is dropped and replaced
 * by a locally generated question for the SAME word so a single bad item never sinks the exam. If
 * the whole batch fails (no network / no key / unparseable), returns an empty list and the composer
 * builds a 100% local, offline exam. The full dictionary ([WordRepository.getAll]) is the distractor
 * pool for local replacements — the interface only passes the small selected set.
 *
 * Items carry blank `id`/`sessionId`; the composer assigns real ids when it persists the session.
 */
class AiQuizSourceImpl(
    private val aiClient: AiClient,
    private val generators: List<LocalQuizGenerator>,
    private val wordRepository: WordRepository,
    private val interfaceLocaleProvider: InterfaceLocaleProvider,
    private val random: Random = Random.Default,
) : AiQuizSource {

    override suspend fun generate(
        words: List<WordEntry>,
        count: Int,
        nativeLang: NativeLang,
    ): List<QuizItem> {
        if (words.isEmpty()) return emptyList()

        val request = QuizGenRequest(
            words = words.map {
                QuizWord(
                    wordId = it.id,
                    term = it.term,
                    translationRu = it.translationRu,
                    translationTk = it.translationTk,
                    contextSentence = it.contextSentence,
                )
            },
            types = AiQuizTypeMapping.AI_TYPES,
            // UI language drives question/explanation wording (spec §6.2, §8.2) and is independent
            // of nativeLang, which only orders the card's translations.
            uiLang = interfaceLocaleProvider.current(),
        )

        // Whole-batch failure -> 100% local exam (offline). getOrElse also covers NoApiKey.
        val generated = aiClient.generateQuizzes(request).getOrElse { return emptyList() }

        val pool = wordRepository.getAll()
        val byWord = generated.groupBy { it.wordId }
        val validIds = words.mapTo(HashSet()) { it.id }

        return words.mapNotNull { word ->
            val ai = byWord[word.id]?.firstOrNull()?.let { quiz ->
                if (isValid(quiz, validIds)) quiz.toQuizItem() else null
            }
            ai ?: localFallback(word, pool, nativeLang)
        }
    }

    /** Client-side validation (spec §6.2): a broken element is dropped and replaced locally. */
    private fun isValid(quiz: GeneratedQuiz, validIds: Set<String>): Boolean =
        quiz.wordId in validIds &&
            quiz.question.isNotBlank() &&
            quiz.options.size == 4 &&
            quiz.options.none { it.isBlank() } &&
            quiz.options.distinct().size == 4 &&
            quiz.answerIndex in quiz.options.indices &&
            AiQuizTypeMapping.typeOf(quiz.type) != null

    private fun GeneratedQuiz.toQuizItem(): QuizItem {
        val type = AiQuizTypeMapping.typeOf(type)!!
        val payload = LocalQuizPayload(
            prompt = question,
            options = options,
            answerIndex = answerIndex,
            answerText = options[answerIndex],
            explanation = explanation,
        )
        return QuizItem(
            id = "",
            sessionId = "",
            wordId = wordId,
            type = type,
            payloadJson = quizJson.encodeToString(LocalQuizPayload.serializer(), payload),
        )
    }

    /** First local generator that can produce a question for [word]; null if the pool is too small. */
    private fun localFallback(
        word: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
    ): QuizItem? {
        for (generator in generators.shuffled(random)) {
            val payload = generator.generate(word, pool, nativeLang, random) ?: continue
            return QuizItem(
                id = "",
                sessionId = "",
                wordId = word.id,
                type = generator.type,
                payloadJson = quizJson.encodeToString(LocalQuizPayload.serializer(), payload),
            )
        }
        return null
    }
}

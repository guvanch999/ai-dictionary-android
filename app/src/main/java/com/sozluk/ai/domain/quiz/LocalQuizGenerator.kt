package com.sozluk.ai.domain.quiz

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.WordEntry
import kotlin.random.Random

/** Smallest word count in the DB for choice-based local quizzes to have enough distractors. */
const val MIN_POOL_FOR_CHOICE = 4

/**
 * Builds one local (AI-free) quiz item for a [target] word (spec §7.2). Returns null when the
 * type cannot be produced for the word (e.g. too few candidate distractors, unsuitable term).
 */
interface LocalQuizGenerator {
    val type: QuizType

    fun generate(
        target: WordEntry,
        pool: List<WordEntry>,
        nativeLang: NativeLang,
        random: Random,
    ): LocalQuizPayload?
}

/**
 * Assembles a 4-option multiple-choice payload with the [correct] answer at a random index.
 * Guarantees exactly one correct option and no duplicate option text; returns null if [distractors]
 * cannot supply three distinct options different from [correct].
 */
internal fun choicePayload(
    prompt: String,
    correct: String,
    distractors: List<String>,
    random: Random,
    ttsText: String? = null,
): LocalQuizPayload? {
    val distinct = distractors.filter { it != correct }.distinct()
    if (distinct.size < 3) return null
    val options = (distinct.take(3) + correct).shuffled(random)
    return LocalQuizPayload(
        prompt = prompt,
        options = options,
        answerIndex = options.indexOf(correct),
        answerText = correct,
        ttsText = ttsText,
    )
}

/**
 * Picks distractor words for a multiple-choice item. Candidates exclude the target and its
 * synonyms; they then prefer the same part of speech and a comparable [key] length (±40%). When
 * fewer than [count] survive, filters are relaxed in order — first length, then part of speech —
 * but the synonym exclusion is always kept. Returns fewer than [count] only if the pool cannot
 * supply more even fully relaxed.
 */
object Distractors {
    fun pick(
        target: WordEntry,
        pool: List<WordEntry>,
        key: (WordEntry) -> String,
        random: Random,
        count: Int = 3,
    ): List<WordEntry> {
        val targetTerm = target.term.lowercase()
        val synonyms = target.synonyms.map { it.lowercase() }.toSet()
        val base = pool.asSequence()
            .filter { it.id != target.id }
            .filter { it.term.lowercase() != targetTerm }
            .filter { it.term.lowercase() !in synonyms }
            .toList()

        val targetLen = key(target).length
        fun sameLength(w: WordEntry): Boolean {
            val len = key(w).length
            return len >= targetLen * 0.6 && len <= targetLen * 1.4
        }
        fun samePos(w: WordEntry): Boolean =
            target.partOfSpeech == null || w.partOfSpeech == target.partOfSpeech

        // Progressive relaxation: strictest first, then drop length, then drop part of speech.
        val filters = listOf<(WordEntry) -> Boolean>(
            { samePos(it) && sameLength(it) },
            { samePos(it) },
            { true },
        )
        for (filter in filters) {
            val candidates = base.filter(filter)
                .distinctBy { key(it) }
                .shuffled(random)
            if (candidates.size >= count) return candidates.take(count)
        }
        // Even fully relaxed there aren't enough; return the most we can, deduped by key.
        return base.distinctBy { key(it) }.shuffled(random).take(count)
    }
}

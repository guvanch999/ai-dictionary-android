package com.sozluk.ai.domain.srs

import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import kotlin.random.Random

/** Which words an exam draws from (spec §7.1, F-3). */
enum class ExamScope { ALL, LAST_WEEK, WEAK, DUE }

/**
 * Weighted, non-uniform selection of words into an exam (spec §7.5): weak words appear far more
 * often than well-learned ones, and MASTERED words only rarely resurface as spot checks. Uses
 * weighted sampling without replacement with an injectable [random] for deterministic tests.
 */
class WordSelector(
    private val random: Random = Random.Default,
) {
    fun select(
        words: List<WordEntry>,
        count: Int,
        scope: ExamScope,
        includeMastered: Boolean,
        now: Long,
    ): List<WordEntry> {
        val pool = words
            .filter { it.inScope(scope, now) }
            .filter { includeMastered || it.status != WordStatus.MASTERED }
            .toMutableList()

        if (count <= 0) return emptyList()
        if (count >= pool.size) return pool.shuffled(random)

        val weights = pool.map { weightOf(it, now) }.toMutableList()
        val picked = ArrayList<WordEntry>(count)
        repeat(count) {
            val total = weights.sum()
            if (total <= 0.0) return@repeat
            var threshold = random.nextDouble() * total
            var index = 0
            while (index < weights.size - 1) {
                threshold -= weights[index]
                if (threshold < 0.0) break
                index++
            }
            picked += pool[index]
            pool.removeAt(index)
            weights.removeAt(index)
        }
        return picked
    }

    private fun WordEntry.inScope(scope: ExamScope, now: Long): Boolean =
        when (scope) {
            ExamScope.ALL -> true
            ExamScope.LAST_WEEK -> createdAt >= now - 7 * LeitnerSchedule.DAY_MS
            ExamScope.WEAK -> box <= 1 || wrongCount > 0
            ExamScope.DUE -> nextReviewAt <= now
        }

    /** Base weight from the spec §7.5 table, doubled when the word is overdue for review. */
    private fun weightOf(word: WordEntry, now: Long): Double {
        val base = when {
            word.status == WordStatus.MASTERED -> 0.1
            word.box <= 1 -> 4.0
            word.box <= 3 -> 2.0
            else -> 1.0
        }
        return if (word.nextReviewAt <= now) base * 2.0 else base
    }
}

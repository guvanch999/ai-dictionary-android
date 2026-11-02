package com.sozluk.ai.domain.srs

import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import kotlin.math.min

/**
 * Applies a finished exam session's results to a single word's SRS state (spec §7.5).
 *
 * [results] holds one boolean per quiz item that targeted the word in the session, so a word
 * that appeared twice (e.g. gap fill + audio) contributes two entries. Per the spec's "нюанс
 * подсчёта", the streak grows by at most 1 per session and any wrong answer resets everything.
 */
class SrsUpdater(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun apply(word: WordEntry, results: List<Boolean>): WordEntry {
        if (results.isEmpty()) return word

        val correct = results.count { it }
        val wrong = results.size - correct
        val correctCount = word.correctCount + correct
        val wrongCount = word.wrongCount + wrong
        val at = now()

        // Any wrong answer in the session sends the word back to box 1 and clears the streak.
        if (wrong > 0) {
            return word.copy(
                box = 1,
                consecutiveCorrect = 0,
                status = WordStatus.LEARNING,
                nextReviewAt = at + LeitnerSchedule.DAY_MS,
                correctCount = correctCount,
                wrongCount = wrongCount,
            )
        }

        // All-correct session: move up one box and raise the streak by exactly one.
        val newBox = min(word.box + 1, LeitnerSchedule.MAX_BOX)
        val newStreak = word.consecutiveCorrect + 1
        val mastered = newStreak >= LeitnerSchedule.MASTERED_STREAK && newBox == LeitnerSchedule.MAX_BOX
        return word.copy(
            box = newBox,
            consecutiveCorrect = newStreak,
            status = if (mastered) WordStatus.MASTERED else word.status,
            nextReviewAt = LeitnerSchedule.nextReviewAt(newBox, at),
            correctCount = correctCount,
            wrongCount = wrongCount,
        )
    }
}

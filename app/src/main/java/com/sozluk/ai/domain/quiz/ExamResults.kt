package com.sozluk.ai.domain.quiz

import com.sozluk.ai.domain.model.QuizItem

/**
 * Aggregates a finished session's answered items into per-word boolean results, exactly the shape
 * [SrsUpdater.apply][com.sozluk.ai.domain.srs.SrsUpdater.apply] expects (spec §7.5): a word that
 * appeared twice contributes two entries, and any wrong answer in its list resets it to box 1.
 * Unanswered items (isCorrect == null) are ignored.
 */
fun aggregateResults(items: List<QuizItem>): Map<String, List<Boolean>> =
    items.filter { it.isCorrect != null }
        .groupBy { it.wordId }
        .mapValues { (_, group) -> group.map { it.isCorrect == true } }

/** Number of correctly answered items in a session. */
fun scoreOf(items: List<QuizItem>): Int = items.count { it.isCorrect == true }

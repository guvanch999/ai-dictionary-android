package com.sozluk.ai.domain.quiz

import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.WordEntry

/**
 * Seam for mixing AI-generated quiz items into a session (spec §7.1, ~40/60 local/AI when online).
 * Implemented in task 08; when absent [ExamComposer] produces a 100% local, offline exam. Returned
 * items must already carry their [QuizItem.sessionId]-less payload; the composer assigns ids/session.
 */
interface AiQuizSource {
    /** Best-effort: may return fewer than [count] items (or none) — the composer fills the rest locally. */
    suspend fun generate(words: List<WordEntry>, count: Int, nativeLang: NativeLang): List<QuizItem>
}

package com.sozluk.ai.domain.model

/** A single question within an exam session (spec §4.2). */
data class QuizItem(
    val id: String,
    val sessionId: String,
    val wordId: String,
    val type: QuizType,
    // Question/options/answer/explanation payload; typed parsing lives in the quiz task.
    val payloadJson: String,
    val userAnswer: String? = null,
    val isCorrect: Boolean? = null,
)

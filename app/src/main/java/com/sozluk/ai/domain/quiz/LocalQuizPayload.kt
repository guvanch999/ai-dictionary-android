package com.sozluk.ai.domain.quiz

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Uniform payload for every locally generated quiz item, serialized into [QuizItem.payloadJson]
 * and parsed by the exam UI (task 08). Multiple-choice types fill [options] + [answerIndex];
 * letter-scramble leaves [options] empty and uses only [answerText]; [ttsText] carries the term
 * to speak for audio questions.
 */
@Serializable
data class LocalQuizPayload(
    val prompt: String,
    val options: List<String> = emptyList(),
    val answerIndex: Int = -1,
    val answerText: String,
    val ttsText: String? = null,
    // AI-generated items carry a short "why" shown after answering; local items leave it null.
    val explanation: String? = null,
)

/** Shared JSON instance for local quiz payloads. */
val quizJson: Json = Json { ignoreUnknownKeys = true }

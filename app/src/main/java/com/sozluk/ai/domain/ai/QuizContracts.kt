package com.sozluk.ai.domain.ai

import com.sozluk.ai.domain.model.QuizType
import kotlinx.serialization.Serializable

/*
 * Placeholder request/response contracts for the quiz-generation (spec §6.2) and open-answer
 * check (spec §6.3, F-4) AI operations. Shapes follow the spec; final fields are settled in
 * tasks 08 (generateQuizzes) and 11 (checkOpenAnswer). Not implemented in this task.
 */

/** One AI-generated quiz item (spec §6.2). `type` is the wire string, e.g. "gap_fill". */
@Serializable
data class GeneratedQuiz(
    val type: String,
    val wordId: String,
    val question: String,
    val options: List<String> = emptyList(),
    val answerIndex: Int,
    val explanation: String,
)

/** A word passed into batch quiz generation, with its translations and source context. */
@Serializable
data class QuizWord(
    val wordId: String,
    val term: String,
    val translationRu: String,
    val translationTk: String,
    val contextSentence: String? = null,
)

/** Batch quiz-generation request (spec §6.2): words + desired task types + UI language. */
@Serializable
data class QuizGenRequest(
    val words: List<QuizWord>,
    val types: List<QuizType>,
    val uiLang: String,
)

/** User's open-ended sentence to grade against a word (spec §6.3, F-4). */
@Serializable
data class OpenAnswerRequest(
    val term: String,
    val article: WordArticle,
    val userSentence: String,
    val uiLang: String,
)

/** Grader verdict for an open answer (spec §6.3). `comment` is in the UI language. */
@Serializable
data class OpenAnswerVerdict(
    val verdict: Verdict,
    val corrected: String? = null,
    val comment: String,
) {
    enum class Verdict { OK, MINOR, WRONG }
}

/**
 * Maps a grader verdict to an SRS correctness result (spec §6.3, task 11A): OK and MINOR both
 * count as a correct answer; only WRONG is an error.
 */
fun OpenAnswerVerdict.Verdict.isCorrect(): Boolean =
    this != OpenAnswerVerdict.Verdict.WRONG

package com.sozluk.ai.domain.ai

/**
 * Single entry point for all AI provider calls (spec §3.2) so a provider — or a proxy backend —
 * can be swapped without touching the rest of the app. Implemented by `AnthropicAiClient` and
 * `GeminiAiClient`; the active one is bound in `di/AiModule`.
 */
interface AiClient {

    /** Word article for [term], explained in the meaning used in [contextSentence] if given. */
    suspend fun explainWord(term: String, contextSentence: String?): Result<WordArticle>

    /** Read a word from a screenshot crop and return its article. */
    suspend fun explainImage(imageBytes: ByteArray, mediaType: String): Result<WordArticle>

    /** Generate a batch of quizzes for the given words. */
    suspend fun generateQuizzes(request: QuizGenRequest): Result<List<GeneratedQuiz>>

    /** Grade a user's open-ended sentence for a word. */
    suspend fun checkOpenAnswer(request: OpenAnswerRequest): Result<OpenAnswerVerdict>
}

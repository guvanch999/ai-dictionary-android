package com.sozluk.ai.data.ai

/**
 * Google Gemini endpoint and model identifiers in one place. Mirrors [AiModels] (Anthropic) but
 * targets the Generative Language API (`generativelanguage.googleapis.com`). The app is wired to
 * this client instead of Anthropic so a Google API key can be used (see AiModule).
 *
 * The request URL is per-model: `$BASE_URL/{model}:generateContent`, with the key sent in the
 * `x-goog-api-key` header (never in the URL, never logged).
 */
object GeminiModels {
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Header carrying the API key. */
    const val API_KEY_HEADER = "x-goog-api-key"

    /**
     * Fast, cheap model for dictionary articles. `gemini-flash-latest` is available on the free
     * tier — this is what makes a plain Google API key work without billing.
     */
    const val ARTICLE_MODEL = "gemini-flash-latest"

    /**
     * Model for quiz generation. Flash handles the batch JSON well and stays on the free tier;
     * bump to a Pro model (e.g. "gemini-2.5-pro") if you want stronger questions and have quota.
     */
    const val QUIZ_MODEL = "gemini-flash-latest"

    /** Vision-capable model for reading a word off a screenshot crop (Flash supports vision). */
    const val VISION_MODEL = "gemini-flash-latest"

    /** Grading open-ended sentences. */
    const val CHECK_MODEL = "gemini-flash-latest"

    /** Output budget for an article response (small, structured JSON). */
    const val ARTICLE_MAX_TOKENS = 1024

    /** Output budget for a vision article response (same structured JSON as an article). */
    const val VISION_MAX_TOKENS = 1024

    /** Output budget for a quiz batch (JSON array of ~10-15 items). */
    const val QUIZ_MAX_TOKENS = 4096

    /** Output budget for an open-answer verdict (small JSON). */
    const val CHECK_MAX_TOKENS = 512

    /** Words per quiz-generation request (spec §6.2: a batch of 10-15, never one at a time). */
    const val QUIZ_CHUNK_SIZE = 15
}

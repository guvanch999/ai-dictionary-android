package com.sozluk.ai.data.ai

/**
 * Anthropic endpoint, protocol and model identifiers in one place (spec §3.1: Haiku for
 * dictionary articles, Sonnet for quiz generation and vision).
 */
object AiModels {
    const val BASE_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** Cheap, fast model for dictionary articles. */
    const val ARTICLE_MODEL = "claude-haiku-4-5"

    /** Stronger model for varied quiz generation. */
    const val QUIZ_MODEL = "claude-sonnet-5"

    /** Vision-capable model for reading a word off a screenshot crop. */
    const val VISION_MODEL = "claude-sonnet-5"

    /** Grading open-ended sentences is a Sonnet-class task (spec §6.3, F-4). */
    const val CHECK_MODEL = "claude-sonnet-5"

    /** Output budget for an article response (small, structured JSON). */
    const val ARTICLE_MAX_TOKENS = 1024

    /** Output budget for a vision article response (same structured JSON as an article). */
    const val VISION_MAX_TOKENS = 1024

    /** Output budget for a quiz batch (JSON array of ~10-15 items). */
    const val QUIZ_MAX_TOKENS = 4096

    /** Output budget for an open-answer verdict (small JSON: verdict + correction + comment). */
    const val CHECK_MAX_TOKENS = 512

    /** Words per quiz-generation request (spec §6.2: a batch of 10-15, never one at a time). */
    const val QUIZ_CHUNK_SIZE = 15
}

package com.sozluk.ai.domain.model

/** How a word was added to the dictionary. */
enum class SourceType { MANUAL, PROCESS_TEXT, SCREENSHOT }

/** Leitner learning status of a word. */
enum class WordStatus { LEARNING, MASTERED }

/** Kinds of quiz questions (local + AI-generated). */
enum class QuizType {
    TRANSLATION_CHOICE,
    REVERSE_CHOICE,
    LETTER_SCRAMBLE,
    AUDIO_CHOICE,
    EXAMPLE_GAP,
    AI_GAP_FILL,
    AI_DEFINITION,
    AI_FIND_MISTAKE,
    AI_SYNONYM,
    AI_DIALOG,
    OPEN_SENTENCE,
}

/** Lifecycle state of an exam session. */
enum class SessionState { IN_PROGRESS, FINISHED }

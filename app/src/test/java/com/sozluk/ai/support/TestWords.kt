package com.sozluk.ai.support

import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus

/** Compact [WordEntry] builder for domain unit tests — only the fields a test cares about. */
fun word(
    id: String,
    term: String = id,
    ru: String = "${id}_ru",
    tk: String = "${id}_tk",
    pos: String? = null,
    synonyms: List<String> = emptyList(),
    examples: List<Example> = emptyList(),
    box: Int = 0,
    consecutiveCorrect: Int = 0,
    correctCount: Int = 0,
    wrongCount: Int = 0,
    status: WordStatus = WordStatus.LEARNING,
    nextReviewAt: Long = 0,
    createdAt: Long = 0,
): WordEntry = WordEntry(
    id = id,
    term = term,
    normalizedTerm = term.lowercase(),
    explanationEn = "explanation of $term",
    translationRu = ru,
    translationTk = tk,
    partOfSpeech = pos,
    synonyms = synonyms,
    examples = examples,
    sourceType = SourceType.MANUAL,
    createdAt = createdAt,
    box = box,
    nextReviewAt = nextReviewAt,
    correctCount = correctCount,
    wrongCount = wrongCount,
    consecutiveCorrect = consecutiveCorrect,
    status = status,
)

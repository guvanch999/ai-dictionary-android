package com.sozluk.ai.domain.model

import com.sozluk.ai.domain.ai.WordArticle
import java.util.UUID

/**
 * Builds a persistable [WordEntry] from an AI [WordArticle]. Used by the add flow and the offline
 * drain worker. [term] is the user's original input; SRS fields start at their defaults.
 * normalizedTerm is finalized by the repository on upsert.
 */
fun WordArticle.toWordEntry(
    term: String,
    contextSentence: String?,
    sourceType: SourceType = SourceType.MANUAL,
    id: String = UUID.randomUUID().toString(),
    createdAt: Long = System.currentTimeMillis(),
    imagePath: String? = null,
): WordEntry = WordEntry(
    id = id,
    term = term.trim(),
    normalizedTerm = term.trim().lowercase(),
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    synonyms = synonyms,
    examples = examples,
    commonMistake = commonMistake,
    contextSentence = contextSentence,
    sourceType = sourceType,
    imagePath = imagePath,
    createdAt = createdAt,
)

package com.sozluk.ai.domain.model

/** A dictionary word/phrase with its AI explanation, translations and SRS state (spec §4.1). */
data class WordEntry(
    val id: String,
    val term: String,
    val normalizedTerm: String,
    val sourceLang: String = "en",
    val explanationEn: String,
    val translationRu: String,
    val translationTk: String,
    val partOfSpeech: String? = null,
    val ipa: String? = null,
    val synonyms: List<String> = emptyList(),
    val examples: List<Example> = emptyList(),
    val commonMistake: String? = null,
    val contextSentence: String? = null,
    val sourceType: SourceType,
    val imagePath: String? = null,
    val createdAt: Long,
    // SRS (Leitner) fields
    val box: Int = 0,
    val nextReviewAt: Long = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val consecutiveCorrect: Int = 0,
    val status: WordStatus = WordStatus.LEARNING,
)

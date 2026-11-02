package com.sozluk.ai.domain.ai

import com.sozluk.ai.domain.model.Example
import kotlinx.serialization.Serializable

/**
 * AI dictionary article for a single term (spec §6.1). Field names mirror the strict JSON
 * schema the model is asked to return and the persisted [com.sozluk.ai.domain.model.WordEntry].
 */
@Serializable
data class WordArticle(
    val term: String,
    val partOfSpeech: String? = null,
    val ipa: String? = null,
    val explanationEn: String,
    val translationRu: String,
    val translationTk: String,
    val synonyms: List<String> = emptyList(),
    val examples: List<Example> = emptyList(),
    val commonMistake: String? = null,
)

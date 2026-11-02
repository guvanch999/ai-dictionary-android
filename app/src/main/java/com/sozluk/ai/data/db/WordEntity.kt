package com.sozluk.ai.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordStatus

/** Room representation of a word (spec §4.1). Lists are stored as JSON strings. */
@Entity(
    tableName = "words",
    indices = [Index("normalizedTerm"), Index("nextReviewAt")],
)
data class WordEntity(
    @PrimaryKey val id: String,
    val term: String,
    val normalizedTerm: String,
    val sourceLang: String,
    val explanationEn: String,
    val translationRu: String,
    val translationTk: String,
    val partOfSpeech: String?,
    val ipa: String?,
    val synonymsJson: String,
    val examplesJson: String,
    val commonMistake: String?,
    val contextSentence: String?,
    val sourceType: SourceType,
    val imagePath: String?,
    val createdAt: Long,
    // SRS (Leitner) fields
    val box: Int,
    val nextReviewAt: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val consecutiveCorrect: Int,
    val status: WordStatus,
)

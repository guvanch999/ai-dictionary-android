package com.sozluk.ai.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sozluk.ai.domain.model.QuizType

/** Room representation of a quiz question (spec §4.2). */
@Entity(
    tableName = "quiz_items",
    indices = [Index("sessionId")],
)
data class QuizItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val wordId: String,
    val type: QuizType,
    val payloadJson: String,
    val userAnswer: String?,
    val isCorrect: Boolean?,
)

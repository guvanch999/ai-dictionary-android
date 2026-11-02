package com.sozluk.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sozluk.ai.domain.model.SessionState

/** Room representation of an exam session (spec §4.2). */
@Entity(tableName = "exam_sessions")
data class ExamSessionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val wordIdsJson: String,
    val mode: String,
    val score: Int,
    val total: Int,
    val state: SessionState,
)

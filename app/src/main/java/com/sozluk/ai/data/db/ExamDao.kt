package com.sozluk.ai.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Upsert
    suspend fun upsertSession(session: ExamSessionEntity)

    @Query(
        "SELECT * FROM exam_sessions WHERE state = 'IN_PROGRESS' ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun getUnfinishedSession(): ExamSessionEntity?

    @Query("SELECT * FROM exam_sessions WHERE id = :id")
    suspend fun getSession(id: String): ExamSessionEntity?

    @Upsert
    suspend fun upsertItems(items: List<QuizItemEntity>)

    @Upsert
    suspend fun upsertItem(item: QuizItemEntity)

    @Query("SELECT * FROM quiz_items WHERE sessionId = :sessionId")
    suspend fun getItemsForSession(sessionId: String): List<QuizItemEntity>

    @Query("SELECT * FROM exam_sessions WHERE state = 'FINISHED' ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<ExamSessionEntity>>
}

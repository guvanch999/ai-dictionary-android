package com.sozluk.ai.domain.repository

import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import kotlinx.coroutines.flow.Flow

interface ExamRepository {
    suspend fun createSession(session: ExamSession)

    suspend fun updateSession(session: ExamSession)

    /** The most recent in-progress session, if any, so exams survive app restarts (spec §4.2). */
    suspend fun getUnfinishedSession(): ExamSession?

    /** A single session by id — used to load results and to guard the idempotent finish. */
    suspend fun getSession(id: String): ExamSession?

    suspend fun upsertItems(items: List<QuizItem>)

    suspend fun updateItem(item: QuizItem)

    suspend fun getItemsForSession(sessionId: String): List<QuizItem>

    /** Finished sessions, newest first. */
    fun observeHistory(): Flow<List<ExamSession>>
}

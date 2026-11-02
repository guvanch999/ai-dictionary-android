package com.sozluk.ai.data.repository

import com.sozluk.ai.data.db.ExamDao
import com.sozluk.ai.data.db.toDomain
import com.sozluk.ai.data.db.toEntity
import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.repository.ExamRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExamRepositoryImpl @Inject constructor(
    private val dao: ExamDao,
) : ExamRepository {

    override suspend fun createSession(session: ExamSession) =
        dao.upsertSession(session.toEntity())

    override suspend fun updateSession(session: ExamSession) =
        dao.upsertSession(session.toEntity())

    override suspend fun getUnfinishedSession(): ExamSession? =
        dao.getUnfinishedSession()?.toDomain()

    override suspend fun getSession(id: String): ExamSession? =
        dao.getSession(id)?.toDomain()

    override suspend fun upsertItems(items: List<QuizItem>) =
        dao.upsertItems(items.map { it.toEntity() })

    override suspend fun updateItem(item: QuizItem) =
        dao.upsertItem(item.toEntity())

    override suspend fun getItemsForSession(sessionId: String): List<QuizItem> =
        dao.getItemsForSession(sessionId).map { it.toDomain() }

    override fun observeHistory(): Flow<List<ExamSession>> =
        dao.observeHistory().map { list -> list.map { it.toDomain() } }
}

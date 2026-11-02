package com.sozluk.ai.data.repository

import com.sozluk.ai.data.db.PendingWordDao
import com.sozluk.ai.data.db.PendingWordEntity
import com.sozluk.ai.domain.model.PendingWord
import com.sozluk.ai.domain.repository.PendingRepository
import java.util.UUID
import javax.inject.Inject

class PendingRepositoryImpl @Inject constructor(
    private val dao: PendingWordDao,
) : PendingRepository {

    override suspend fun add(term: String, contextSentence: String?): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            PendingWordEntity(
                id = id,
                term = term.trim(),
                contextSentence = contextSentence,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    override suspend fun getAll(): List<PendingWord> =
        dao.getAll().map { PendingWord(it.id, it.term, it.contextSentence, it.createdAt) }

    override suspend fun remove(id: String) = dao.deleteById(id)
}

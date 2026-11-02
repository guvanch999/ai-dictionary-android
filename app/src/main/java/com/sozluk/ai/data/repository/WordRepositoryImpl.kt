package com.sozluk.ai.data.repository

import com.sozluk.ai.data.db.WordDao
import com.sozluk.ai.data.db.toDomain
import com.sozluk.ai.data.db.toEntity
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import com.sozluk.ai.domain.repository.CropStore
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.repository.WordSort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WordRepositoryImpl @Inject constructor(
    private val dao: WordDao,
    private val cropStore: CropStore,
) : WordRepository {

    override suspend fun upsert(word: WordEntry) {
        // Never trust the caller for normalizedTerm (spec §5).
        val normalized = word.term.trim().lowercase()
        dao.upsert(word.copy(normalizedTerm = normalized).toEntity())
    }

    override suspend fun getById(id: String): WordEntry? = dao.getById(id)?.toDomain()

    override suspend fun getAll(): List<WordEntry> = dao.getAll().map { it.toDomain() }

    override suspend fun deleteById(id: String) {
        // Remove the screenshot crop file (if any) before dropping the row (spec F-6, step 5).
        dao.getById(id)?.imagePath?.let { cropStore.delete(it) }
        dao.deleteById(id)
    }

    override fun observeAll(sort: WordSort): Flow<List<WordEntry>> {
        val source = when (sort) {
            WordSort.DATE -> dao.observeAllByDate()
            WordSort.ALPHABETICAL -> dao.observeAllAlphabetical()
            WordSort.WEAKNESS -> dao.observeAllByWeakness()
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun search(query: String): Flow<List<WordEntry>> {
        val pattern = "%${query.trim().lowercase()}%"
        return dao.search(pattern).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun findByNormalizedTerm(term: String): List<WordEntry> =
        dao.findByNormalizedTerm(term.trim().lowercase()).map { it.toDomain() }

    override suspend fun getDueForReview(now: Long): List<WordEntry> =
        dao.getDueForReview(now).map { it.toDomain() }

    override suspend fun countByStatus(status: WordStatus): Int =
        dao.countByStatus(status.name)
}

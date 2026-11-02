package com.sozluk.ai.domain.repository

import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import kotlinx.coroutines.flow.Flow

/** Sort orders for the word list (spec §5, F-2). */
enum class WordSort { DATE, ALPHABETICAL, WEAKNESS }

interface WordRepository {
    /** Persists a word; fills normalizedTerm from term (spec §5) — callers need not set it. */
    suspend fun upsert(word: WordEntry)

    suspend fun getById(id: String): WordEntry?

    /** The whole dictionary as a one-shot list — the exam candidate pool (spec §7.5). */
    suspend fun getAll(): List<WordEntry>

    suspend fun deleteById(id: String)

    fun observeAll(sort: WordSort): Flow<List<WordEntry>>

    /** Case-insensitive LIKE search over term/ru/tk translations. */
    fun search(query: String): Flow<List<WordEntry>>

    /** All words matching a normalized term — used for dedup (spec §4.3). */
    suspend fun findByNormalizedTerm(term: String): List<WordEntry>

    suspend fun getDueForReview(now: Long): List<WordEntry>

    suspend fun countByStatus(status: WordStatus): Int
}

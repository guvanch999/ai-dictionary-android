package com.sozluk.ai.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Upsert
    suspend fun upsert(entity: WordEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WordEntity>)

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: String): WordEntity?

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: String)

    // Whole dictionary as a one-shot list — used to build the exam candidate pool (spec §7.5).
    @Query("SELECT * FROM words")
    suspend fun getAll(): List<WordEntity>

    @Query("SELECT * FROM words ORDER BY createdAt DESC")
    fun observeAllByDate(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words ORDER BY normalizedTerm ASC")
    fun observeAllAlphabetical(): Flow<List<WordEntity>>

    // "Weak words": most wrong answers first (spec §5, F-2).
    @Query("SELECT * FROM words ORDER BY wrongCount DESC")
    fun observeAllByWeakness(): Flow<List<WordEntity>>

    // Caller passes an already-wrapped %pattern%.
    @Query(
        """
        SELECT * FROM words
        WHERE normalizedTerm LIKE :pattern
           OR translationRu LIKE :pattern
           OR translationTk LIKE :pattern
        ORDER BY normalizedTerm ASC
        """,
    )
    fun search(pattern: String): Flow<List<WordEntity>>

    // All matches — duplicates are allowed for dedup handling (spec §4.3).
    @Query("SELECT * FROM words WHERE normalizedTerm = :term")
    suspend fun findByNormalizedTerm(term: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE nextReviewAt <= :now ORDER BY nextReviewAt ASC")
    suspend fun getDueForReview(now: Long): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}

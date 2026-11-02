package com.sozluk.ai.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingWordDao {
    @Upsert
    suspend fun upsert(entity: PendingWordEntity)

    @Query("SELECT * FROM pending_words ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingWordEntity>

    @Query("DELETE FROM pending_words WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pending_words")
    fun observeCount(): Flow<Int>
}

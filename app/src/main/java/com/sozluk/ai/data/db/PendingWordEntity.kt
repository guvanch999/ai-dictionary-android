package com.sozluk.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A word queued for AI explanation while offline (spec F-1.5). Drained by DrainPendingWorker
 * once the network is back. Added in schema v2 (MIGRATION_1_2).
 */
@Entity(tableName = "pending_words")
data class PendingWordEntity(
    @PrimaryKey val id: String,
    val term: String,
    val contextSentence: String?,
    val createdAt: Long,
)

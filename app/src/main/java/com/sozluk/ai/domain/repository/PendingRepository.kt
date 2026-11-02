package com.sozluk.ai.domain.repository

import com.sozluk.ai.domain.model.PendingWord

/** Offline queue of words awaiting AI explanation (spec F-1.5). */
interface PendingRepository {
    /** Stores a word to explain later; returns the generated id. */
    suspend fun add(term: String, contextSentence: String?): String

    suspend fun getAll(): List<PendingWord>

    suspend fun remove(id: String)
}

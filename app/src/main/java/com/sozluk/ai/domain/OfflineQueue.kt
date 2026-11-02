package com.sozluk.ai.domain

/**
 * Enqueues a word for later AI explanation when the request failed due to no network (spec F-1.5).
 * A thin seam so the ViewModel stays free of WorkManager and is unit-testable.
 */
interface OfflineQueue {
    suspend fun enqueue(term: String, contextSentence: String?)
}

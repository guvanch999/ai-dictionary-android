package com.sozluk.ai.domain.model

/** A word queued for AI explanation while offline (spec F-1.5). */
data class PendingWord(
    val id: String,
    val term: String,
    val contextSentence: String?,
    val createdAt: Long,
)

package com.sozluk.ai.domain.model

/** A quiz session over a set of words, survivable across app restarts (spec §4.2). */
data class ExamSession(
    val id: String,
    val createdAt: Long,
    val wordIds: List<String> = emptyList(),
    val mode: String,
    val score: Int = 0,
    val total: Int = 0,
    val state: SessionState = SessionState.IN_PROGRESS,
)

package com.sozluk.ai.domain.srs

/** Simplified Leitner schedule: 6 boxes (0..5) with fixed review intervals (spec §7.4). */
object LeitnerSchedule {
    const val MAX_BOX = 5

    /** Days until the next review, indexed by box. */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 16, 35)

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** consecutiveCorrect threshold that, together with [MAX_BOX], promotes a word to MASTERED. */
    const val MASTERED_STREAK = 5

    fun nextReviewAt(box: Int, now: Long): Long =
        now + INTERVAL_DAYS[box.coerceIn(0, MAX_BOX)].toLong() * DAY_MS
}

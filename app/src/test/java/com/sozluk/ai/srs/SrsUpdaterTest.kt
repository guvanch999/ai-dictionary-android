package com.sozluk.ai.srs

import com.sozluk.ai.domain.model.WordStatus
import com.sozluk.ai.domain.srs.LeitnerSchedule
import com.sozluk.ai.domain.srs.SrsUpdater
import com.sozluk.ai.support.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SrsUpdaterTest {

    private val now = 1_700_000_000_000L
    private val updater = SrsUpdater { now }

    @Test
    fun fiveCorrectSessionsInARow_reachMastered() {
        var w = word("w", box = 0, consecutiveCorrect = 0)
        repeat(4) {
            w = updater.apply(w, listOf(true))
            assertEquals(WordStatus.LEARNING, w.status)
        }
        // Fifth all-correct session: box hits 5 and streak hits 5 → MASTERED.
        w = updater.apply(w, listOf(true))
        assertEquals(5, w.box)
        assertEquals(5, w.consecutiveCorrect)
        assertEquals(WordStatus.MASTERED, w.status)
        assertEquals(5, w.correctCount)
    }

    @Test
    fun anyWrongAnswer_resetsEverything() {
        val mastered = word(
            "w", box = 5, consecutiveCorrect = 5, correctCount = 9,
            status = WordStatus.MASTERED,
        )
        val after = updater.apply(mastered, listOf(false))
        assertEquals(1, after.box)
        assertEquals(0, after.consecutiveCorrect)
        assertEquals(WordStatus.LEARNING, after.status)
        assertEquals(now + LeitnerSchedule.DAY_MS, after.nextReviewAt)
        assertEquals(1, after.wrongCount)
    }

    @Test
    fun twoCorrectItemsOnOneWord_raiseStreakByOne() {
        val w = word("w", box = 0, consecutiveCorrect = 0)
        val after = updater.apply(w, listOf(true, true))
        assertEquals(1, after.consecutiveCorrect)
        assertEquals(1, after.box)
        assertEquals(2, after.correctCount)
    }

    @Test
    fun mixedResults_takeTheWrongBranch() {
        val w = word("w", box = 4, consecutiveCorrect = 4)
        val after = updater.apply(w, listOf(true, false))
        assertEquals(1, after.box)
        assertEquals(0, after.consecutiveCorrect)
        assertEquals(WordStatus.LEARNING, after.status)
        assertEquals(1, after.correctCount)
        assertEquals(1, after.wrongCount)
    }

    @Test
    fun correctAnswer_reschedulesByNewBoxInterval() {
        for (box in 0..4) {
            val after = updater.apply(word("w", box = box), listOf(true))
            val expected = now + LeitnerSchedule.INTERVAL_DAYS[box + 1] * LeitnerSchedule.DAY_MS
            assertEquals("box $box -> ${box + 1}", expected, after.nextReviewAt)
        }
    }

    @Test
    fun boxNeverExceedsFive() {
        val after = updater.apply(word("w", box = 5, consecutiveCorrect = 2), listOf(true))
        assertEquals(5, after.box)
        assertEquals(3, after.consecutiveCorrect)
    }

    @Test
    fun emptyResults_returnWordUnchanged() {
        val w = word("w", box = 3, consecutiveCorrect = 2)
        assertSame(w, updater.apply(w, emptyList()))
    }
}

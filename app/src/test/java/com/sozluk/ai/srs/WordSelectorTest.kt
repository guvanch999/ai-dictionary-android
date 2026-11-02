package com.sozluk.ai.srs

import com.sozluk.ai.domain.model.WordStatus
import com.sozluk.ai.domain.srs.ExamScope
import com.sozluk.ai.domain.srs.LeitnerSchedule
import com.sozluk.ai.domain.srs.WordSelector
import com.sozluk.ai.support.word
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSelectorTest {

    private val now = 1_700_000_000_000L
    private val future = now + 10 * LeitnerSchedule.DAY_MS

    @Test
    fun weightDistribution_favoursWeakWords() {
        // Not-due words so the ×2 overdue multiplier stays out of the ratios.
        val weak = word("weak", box = 0, nextReviewAt = future) // weight 4.0
        val learning = word("mid", box = 2, nextReviewAt = future) // weight 2.0
        val strong = word("strong", box = 5, nextReviewAt = future) // weight 1.0
        val mastered = word(
            "mastered", box = 5, consecutiveCorrect = 5,
            status = WordStatus.MASTERED, nextReviewAt = future,
        ) // weight 0.1
        val words = listOf(weak, learning, strong, mastered)

        val selector = WordSelector(Random(42))
        val counts = HashMap<String, Int>()
        val n = 40_000
        repeat(n) {
            val picked = selector.select(words, count = 1, ExamScope.ALL, includeMastered = true, now = future)
            counts.merge(picked.single().id, 1, Int::plus)
        }

        val strongCount = counts.getValue("strong").toDouble()
        assertEquals(4.0, counts.getValue("weak") / strongCount, 0.5)
        assertEquals(2.0, counts.getValue("mid") / strongCount, 0.4)
        assertEquals(0.1, counts.getValue("mastered") / strongCount, 0.1)
    }

    @Test
    fun includeMasteredFalse_excludesMasteredEntirely() {
        val words = listOf(
            word("a", box = 0, nextReviewAt = future),
            word("m", status = WordStatus.MASTERED, box = 5, nextReviewAt = future),
        )
        repeat(1_000) {
            val picked = WordSelector(Random(it.toLong()))
                .select(words, count = 5, ExamScope.ALL, includeMastered = false, now = future)
            assertFalse(picked.any { w -> w.id == "m" })
        }
    }

    @Test
    fun overdueWord_getsDoubleWeight() {
        val due = word("due", box = 2, nextReviewAt = now - 1) // 2.0 * 2 = 4.0
        val notDue = word("fresh", box = 2, nextReviewAt = future) // 2.0
        val words = listOf(due, notDue)

        val selector = WordSelector(Random(7))
        var dueCount = 0
        val n = 20_000
        repeat(n) {
            if (selector.select(words, 1, ExamScope.ALL, includeMastered = true, now).single().id == "due") {
                dueCount++
            }
        }
        // due:notDue weights are 4:2, so due should be picked ~2× as often.
        assertEquals(2.0, dueCount.toDouble() / (n - dueCount), 0.2)
    }

    @Test
    fun fixedSeed_isDeterministic() {
        val words = (1..6).map { word("w$it", box = it % 5, nextReviewAt = future) }
        val a = WordSelector(Random(99)).select(words, 3, ExamScope.ALL, includeMastered = true, now)
        val b = WordSelector(Random(99)).select(words, 3, ExamScope.ALL, includeMastered = true, now)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun scopeFilters_selectTheRightSubset() {
        // box = 3 keeps recent/old out of the WEAK scope so each scope assertion is unambiguous.
        val recent = word("recent", box = 3, createdAt = now - LeitnerSchedule.DAY_MS, nextReviewAt = future)
        val old = word("old", box = 3, createdAt = now - 30 * LeitnerSchedule.DAY_MS, nextReviewAt = future)
        val due = word("due", box = 3, nextReviewAt = now - 1)
        val weak = word("weak", box = 1, nextReviewAt = future)
        val hardWrong = word("hardWrong", box = 4, wrongCount = 3, nextReviewAt = future)
        val words = listOf(recent, old, due, weak, hardWrong)

        // count >= pool returns the whole in-scope pool, isolating the scope filter.
        fun ids(scope: ExamScope) =
            WordSelector(Random(1)).select(words, 100, scope, includeMastered = true, now)
                .map { it.id }.toSet()

        assertEquals(setOf("recent", "old", "due", "weak", "hardWrong"), ids(ExamScope.ALL))
        assertTrue("recent" in ids(ExamScope.LAST_WEEK))
        assertFalse("old" in ids(ExamScope.LAST_WEEK))
        assertEquals(setOf("due"), ids(ExamScope.DUE))
        assertEquals(setOf("weak", "hardWrong"), ids(ExamScope.WEAK))
    }

    @Test
    fun countGreaterThanPool_returnsAll() {
        val words = listOf(word("a", nextReviewAt = future), word("b", nextReviewAt = future))
        val picked = WordSelector(Random(3)).select(words, 10, ExamScope.ALL, includeMastered = true, now)
        assertEquals(setOf("a", "b"), picked.map { it.id }.toSet())
    }
}

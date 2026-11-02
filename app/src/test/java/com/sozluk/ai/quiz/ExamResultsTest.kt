package com.sozluk.ai.quiz

import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.quiz.aggregateResults
import com.sozluk.ai.domain.quiz.scoreOf
import com.sozluk.ai.domain.srs.SrsUpdater
import com.sozluk.ai.support.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamResultsTest {

    private fun item(wordId: String, isCorrect: Boolean?) = QuizItem(
        id = "i-$wordId-$isCorrect",
        sessionId = "s",
        wordId = wordId,
        type = QuizType.TRANSLATION_CHOICE,
        payloadJson = "{}",
        userAnswer = if (isCorrect == null) null else "x",
        isCorrect = isCorrect,
    )

    @Test
    fun aggregateResults_groupsPerWord_andKeepsMultipleEntries() {
        val items = listOf(
            item("w1", true),
            item("w1", false), // same word appears twice
            item("w2", true),
        )
        val result = aggregateResults(items)
        assertEquals(listOf(true, false), result["w1"])
        assertEquals(listOf(true), result["w2"])
    }

    @Test
    fun aggregateResults_ignoresUnansweredItems() {
        val items = listOf(item("w1", true), item("w1", null))
        assertEquals(listOf(true), aggregateResults(items)["w1"])
    }

    @Test
    fun scoreOf_countsOnlyCorrect() {
        val items = listOf(item("w1", true), item("w2", false), item("w3", null), item("w4", true))
        assertEquals(2, scoreOf(items))
    }

    @Test
    fun aggregated_wrongEntry_resetsWordToBoxOne_viaSrsUpdater() {
        // A word answered correctly once and wrong once should be reset by SrsUpdater (any wrong).
        val start = word("w1", box = 3, consecutiveCorrect = 2)
        val results = aggregateResults(listOf(item("w1", true), item("w1", false)))["w1"]!!
        val updated = SrsUpdater { 1_000L }.apply(start, results)
        assertEquals(1, updated.box)
        assertEquals(0, updated.consecutiveCorrect)
        assertTrue(updated.wrongCount >= 1)
    }
}

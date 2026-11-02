package com.sozluk.ai.ai

import com.sozluk.ai.domain.ai.OpenAnswerVerdict.Verdict
import com.sozluk.ai.domain.ai.isCorrect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAnswerScoringTest {

    @Test
    fun okAndMinorCountAsCorrect_wrongDoesNot() {
        assertTrue(Verdict.OK.isCorrect())
        assertTrue(Verdict.MINOR.isCorrect())
        assertFalse(Verdict.WRONG.isCorrect())
    }
}

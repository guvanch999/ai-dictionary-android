package com.sozluk.ai

import com.sozluk.ai.data.db.Converters
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun sourceType_roundTrips_allValues() {
        for (value in SourceType.entries) {
            val restored = converters.toSourceType(converters.fromSourceType(value))
            assertEquals(value, restored)
        }
    }

    @Test
    fun wordStatus_roundTrips_allValues() {
        for (value in WordStatus.entries) {
            val restored = converters.toWordStatus(converters.fromWordStatus(value))
            assertEquals(value, restored)
        }
    }

    @Test
    fun quizType_roundTrips_allValues() {
        for (value in QuizType.entries) {
            val restored = converters.toQuizType(converters.fromQuizType(value))
            assertEquals(value, restored)
        }
    }

    @Test
    fun sessionState_roundTrips_allValues() {
        for (value in SessionState.entries) {
            val restored = converters.toSessionState(converters.fromSessionState(value))
            assertEquals(value, restored)
        }
    }

    @Test
    fun enums_storedByName_notOrdinal() {
        // Persisting by name guards against reordering breaking existing rows.
        assertEquals("SCREENSHOT", converters.fromSourceType(SourceType.SCREENSHOT))
        assertEquals("MASTERED", converters.fromWordStatus(WordStatus.MASTERED))
        assertEquals("OPEN_SENTENCE", converters.fromQuizType(QuizType.OPEN_SENTENCE))
        assertEquals("FINISHED", converters.fromSessionState(SessionState.FINISHED))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownValue_throws() {
        converters.toWordStatus("NOT_A_STATUS")
    }
}

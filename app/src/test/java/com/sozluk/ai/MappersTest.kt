package com.sozluk.ai

import com.sozluk.ai.data.db.toDomain
import com.sozluk.ai.data.db.toEntity
import com.sozluk.ai.domain.model.Example
import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun wordEntry_roundTrips_throughEntity() {
        val word = WordEntry(
            id = "w1",
            term = "bank",
            normalizedTerm = "bank",
            sourceLang = "en",
            explanationEn = "the land beside a river",
            translationRu = "берег",
            translationTk = "kenar",
            partOfSpeech = "noun",
            ipa = "/bæŋk/",
            synonyms = listOf("shore", "riverside"),
            examples = listOf(
                Example("We sat on the bank.", "Мы сидели на берегу.", "Biz kenarda oturdyk."),
            ),
            commonMistake = "not a financial bank",
            contextSentence = "We walked along the river bank.",
            sourceType = SourceType.PROCESS_TEXT,
            imagePath = null,
            createdAt = 123L,
            box = 2,
            nextReviewAt = 456L,
            correctCount = 3,
            wrongCount = 1,
            consecutiveCorrect = 2,
            status = WordStatus.LEARNING,
        )

        assertEquals(word, word.toEntity().toDomain())
    }

    @Test
    fun wordEntry_roundTrips_withEmptyLists() {
        val word = baseWord().copy(synonyms = emptyList(), examples = emptyList())
        assertEquals(word, word.toEntity().toDomain())
    }

    @Test
    fun wordEntry_serializesLists_intoJsonColumns() {
        val entity = baseWord().copy(
            synonyms = listOf("a", "b"),
            examples = listOf(Example("e", "р", "t")),
        ).toEntity()

        assertEquals("""["a","b"]""", entity.synonymsJson)
        assert(entity.examplesJson.contains("\"en\":\"e\""))
    }

    @Test
    fun examSession_roundTrips_throughEntity() {
        val session = ExamSession(
            id = "s1",
            createdAt = 10L,
            wordIds = listOf("w1", "w2", "w3"),
            mode = "srs",
            score = 2,
            total = 3,
            state = SessionState.FINISHED,
        )
        assertEquals(session, session.toEntity().toDomain())
    }

    @Test
    fun quizItem_roundTrips_throughEntity() {
        val item = QuizItem(
            id = "q1",
            sessionId = "s1",
            wordId = "w1",
            type = QuizType.TRANSLATION_CHOICE,
            payloadJson = """{"question":"?","options":["a","b"],"answerIndex":0}""",
            userAnswer = "a",
            isCorrect = true,
        )
        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun quizItem_roundTrips_withNullAnswer() {
        val item = QuizItem(
            id = "q2",
            sessionId = "s1",
            wordId = "w1",
            type = QuizType.OPEN_SENTENCE,
            payloadJson = "{}",
            userAnswer = null,
            isCorrect = null,
        )
        assertEquals(item, item.toEntity().toDomain())
    }

    private fun baseWord() = WordEntry(
        id = "w",
        term = "term",
        normalizedTerm = "term",
        explanationEn = "e",
        translationRu = "р",
        translationTk = "t",
        sourceType = SourceType.MANUAL,
        createdAt = 0L,
    )
}

package com.sozluk.ai.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sozluk.ai.data.db.AppDatabase
import com.sozluk.ai.data.db.WordDao
import com.sozluk.ai.data.db.WordEntity
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.wordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun word(
        id: String,
        term: String = id,
        normalized: String = term.lowercase(),
        translationRu: String = "ru_$id",
        translationTk: String = "tk_$id",
        createdAt: Long = 0L,
        wrongCount: Int = 0,
        nextReviewAt: Long = 0L,
        status: WordStatus = WordStatus.LEARNING,
    ) = WordEntity(
        id = id,
        term = term,
        normalizedTerm = normalized,
        sourceLang = "en",
        explanationEn = "e",
        translationRu = translationRu,
        translationTk = translationTk,
        partOfSpeech = null,
        ipa = null,
        synonymsJson = "[]",
        examplesJson = "[]",
        commonMistake = null,
        contextSentence = null,
        sourceType = SourceType.MANUAL,
        imagePath = null,
        createdAt = createdAt,
        box = 0,
        nextReviewAt = nextReviewAt,
        correctCount = 0,
        wrongCount = wrongCount,
        consecutiveCorrect = 0,
        status = status,
    )

    @Test
    fun upsert_getById_deleteById() = runTest {
        val w = word("w1")
        dao.upsert(w)
        assertEquals(w, dao.getById("w1"))

        dao.deleteById("w1")
        assertNull(dao.getById("w1"))
    }

    @Test
    fun observeAllByDate_ordersByCreatedAtDesc() = runTest {
        dao.upsert(word("a", createdAt = 1L))
        dao.upsert(word("b", createdAt = 3L))
        dao.upsert(word("c", createdAt = 2L))

        val ids = dao.observeAllByDate().first().map { it.id }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun observeAllAlphabetical_ordersByNormalizedTerm() = runTest {
        dao.upsert(word("1", normalized = "banana"))
        dao.upsert(word("2", normalized = "apple"))
        dao.upsert(word("3", normalized = "cherry"))

        val terms = dao.observeAllAlphabetical().first().map { it.normalizedTerm }
        assertEquals(listOf("apple", "banana", "cherry"), terms)
    }

    @Test
    fun observeAllByWeakness_ordersByWrongCountDesc() = runTest {
        dao.upsert(word("a", wrongCount = 1))
        dao.upsert(word("b", wrongCount = 5))
        dao.upsert(word("c", wrongCount = 3))

        val ids = dao.observeAllByWeakness().first().map { it.id }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun search_matchesNormalizedTermAndTranslations() = runTest {
        dao.upsert(word("1", normalized = "river", translationRu = "река", translationTk = "derýa"))
        dao.upsert(word("2", normalized = "mountain", translationRu = "гора", translationTk = "dag"))

        val byTerm = dao.search("%riv%").first().map { it.id }
        assertEquals(listOf("1"), byTerm)

        val byRu = dao.search("%гор%").first().map { it.id }
        assertEquals(listOf("2"), byRu)

        val byTk = dao.search("%dag%").first().map { it.id }
        assertEquals(listOf("2"), byTk)
    }

    @Test
    fun findByNormalizedTerm_returnsAllDuplicates() = runTest {
        // Duplicates are allowed (spec §4.3).
        dao.upsert(word("1", normalized = "bank"))
        dao.upsert(word("2", normalized = "bank"))
        dao.upsert(word("3", normalized = "other"))

        val matches = dao.findByNormalizedTerm("bank").map { it.id }.sorted()
        assertEquals(listOf("1", "2"), matches)
    }

    @Test
    fun getDueForReview_returnsOnlyDue_orderedByNextReview() = runTest {
        dao.upsert(word("past", nextReviewAt = 50L))
        dao.upsert(word("now", nextReviewAt = 100L))
        dao.upsert(word("future", nextReviewAt = 150L))

        val due = dao.getDueForReview(100L).map { it.id }
        assertEquals(listOf("past", "now"), due)
    }

    @Test
    fun countByStatus_countsMatching() = runTest {
        dao.upsert(word("a", status = WordStatus.LEARNING))
        dao.upsert(word("b", status = WordStatus.LEARNING))
        dao.upsert(word("c", status = WordStatus.MASTERED))

        assertEquals(2, dao.countByStatus(WordStatus.LEARNING.name))
        assertEquals(1, dao.countByStatus(WordStatus.MASTERED.name))
    }

    @Test
    fun upsert_replacesExisting() = runTest {
        dao.upsert(word("w1", translationRu = "старый"))
        dao.upsert(word("w1", translationRu = "новый"))

        assertEquals("новый", dao.getById("w1")?.translationRu)
        assertTrue(dao.observeAllByDate().first().size == 1)
    }
}

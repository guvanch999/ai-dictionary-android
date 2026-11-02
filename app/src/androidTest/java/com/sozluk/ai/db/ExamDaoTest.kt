package com.sozluk.ai.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sozluk.ai.data.db.AppDatabase
import com.sozluk.ai.data.db.ExamDao
import com.sozluk.ai.data.db.ExamSessionEntity
import com.sozluk.ai.data.db.QuizItemEntity
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExamDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.examDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun session(
        id: String,
        createdAt: Long = 0L,
        state: SessionState = SessionState.IN_PROGRESS,
        score: Int = 0,
        total: Int = 0,
    ) = ExamSessionEntity(
        id = id,
        createdAt = createdAt,
        wordIdsJson = "[]",
        mode = "srs",
        score = score,
        total = total,
        state = state,
    )

    private fun item(id: String, sessionId: String) = QuizItemEntity(
        id = id,
        sessionId = sessionId,
        wordId = "w1",
        type = QuizType.TRANSLATION_CHOICE,
        payloadJson = "{}",
        userAnswer = null,
        isCorrect = null,
    )

    @Test
    fun upsertSession_andUpdateStateScore() = runTest {
        dao.upsertSession(session("s1"))
        dao.upsertSession(session("s1", state = SessionState.FINISHED, score = 4, total = 5))

        val history = dao.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals(4, history.first().score)
        assertEquals(SessionState.FINISHED, history.first().state)
    }

    @Test
    fun getUnfinishedSession_returnsMostRecentInProgress() = runTest {
        dao.upsertSession(session("old", createdAt = 1L))
        dao.upsertSession(session("new", createdAt = 5L))
        dao.upsertSession(session("done", createdAt = 9L, state = SessionState.FINISHED))

        assertEquals("new", dao.getUnfinishedSession()?.id)
    }

    @Test
    fun getUnfinishedSession_nullWhenNone() = runTest {
        dao.upsertSession(session("done", state = SessionState.FINISHED))
        assertNull(dao.getUnfinishedSession())
    }

    @Test
    fun getSession_roundTripsById() = runTest {
        dao.upsertSession(session("s1", createdAt = 7L, state = SessionState.FINISHED, score = 3, total = 5))

        val loaded = dao.getSession("s1")
        assertEquals(7L, loaded?.createdAt)
        assertEquals(3, loaded?.score)
        assertEquals(SessionState.FINISHED, loaded?.state)
        assertNull(dao.getSession("missing"))
    }

    @Test
    fun upsertItems_andReadBySession() = runTest {
        dao.upsertSession(session("s1"))
        dao.upsertItems(listOf(item("q1", "s1"), item("q2", "s1"), item("q3", "s2")))

        val items = dao.getItemsForSession("s1").map { it.id }.sorted()
        assertEquals(listOf("q1", "q2"), items)
    }

    @Test
    fun upsertItem_updatesAnswer() = runTest {
        dao.upsertSession(session("s1"))
        dao.upsertItems(listOf(item("q1", "s1")))

        dao.upsertItem(item("q1", "s1").copy(userAnswer = "a", isCorrect = true))

        val stored = dao.getItemsForSession("s1").first()
        assertEquals("a", stored.userAnswer)
        assertEquals(true, stored.isCorrect)
    }

    @Test
    fun observeHistory_onlyFinished_newestFirst() = runTest {
        dao.upsertSession(session("a", createdAt = 1L, state = SessionState.FINISHED))
        dao.upsertSession(session("b", createdAt = 3L, state = SessionState.FINISHED))
        dao.upsertSession(session("c", createdAt = 2L, state = SessionState.IN_PROGRESS))

        val ids = dao.observeHistory().first().map { it.id }
        assertEquals(listOf("b", "a"), ids)
    }
}

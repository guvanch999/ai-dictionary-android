package com.sozluk.ai.quiz

import androidx.lifecycle.SavedStateHandle
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.ai.OpenAnswerVerdict
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.model.ExamSession
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.srs.SrsUpdater
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.support.word
import com.sozluk.ai.ui.navigation.Routes
import com.sozluk.ai.ui.screens.exam.ExamViewModel
import com.sozluk.ai.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExamViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sessionId = "s1"

    private fun mcPayload(answerIndex: Int): String {
        val opts = listOf("a", "b", "c", "d")
        return quizJson.encodeToString(
            LocalQuizPayload.serializer(),
            LocalQuizPayload(prompt = "q", options = opts, answerIndex = answerIndex, answerText = opts[answerIndex]),
        )
    }

    private fun item(id: String, wordId: String, answerIndex: Int, userAnswer: String? = null, isCorrect: Boolean? = null) =
        QuizItem(
            id = id,
            sessionId = sessionId,
            wordId = wordId,
            type = QuizType.TRANSLATION_CHOICE,
            payloadJson = mcPayload(answerIndex),
            userAnswer = userAnswer,
            isCorrect = isCorrect,
        )

    /** In-memory ExamRepository so finish() can observe the state it just committed. */
    private class FakeExamRepository(
        initial: ExamSession,
        items: List<QuizItem>,
    ) : ExamRepository {
        var session: ExamSession = initial
        val items: MutableList<QuizItem> = items.toMutableList()

        override suspend fun createSession(session: ExamSession) { this.session = session }
        override suspend fun updateSession(session: ExamSession) { this.session = session }
        override suspend fun getUnfinishedSession(): ExamSession? =
            session.takeIf { it.state == SessionState.IN_PROGRESS }
        override suspend fun getSession(id: String): ExamSession? = session.takeIf { it.id == id }
        override suspend fun upsertItems(items: List<QuizItem>) { this.items.clear(); this.items.addAll(items) }
        override suspend fun updateItem(item: QuizItem) {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) items[idx] = item
        }
        override suspend fun getItemsForSession(sessionId: String): List<QuizItem> =
            items.filter { it.sessionId == sessionId }
        override fun observeHistory(): Flow<List<ExamSession>> = emptyFlow()
    }

    private fun session(state: SessionState) = ExamSession(
        id = sessionId,
        createdAt = 0,
        wordIds = listOf("w1", "w2"),
        mode = "ALL",
        state = state,
    )

    private fun wordRepo(): WordRepository {
        val repo = mockk<WordRepository>(relaxed = true)
        coEvery { repo.getById("w1") } returns word("w1")
        coEvery { repo.getById("w2") } returns word("w2")
        return repo
    }

    private val locale = InterfaceLocaleProvider { "en" }

    private fun makeVm(
        examRepo: ExamRepository,
        words: WordRepository,
        ai: AiClient = mockk(relaxed = true),
    ) = ExamViewModel(
        SavedStateHandle(mapOf(Routes.ARG_SESSION_ID to sessionId)),
        examRepo,
        words,
        SrsUpdater { 0L },
        ai,
        locale,
    )

    private fun openItem(id: String, wordId: String, term: String = "bank") = QuizItem(
        id = id,
        sessionId = sessionId,
        wordId = wordId,
        type = QuizType.OPEN_SENTENCE,
        payloadJson = quizJson.encodeToString(
            LocalQuizPayload.serializer(),
            LocalQuizPayload(prompt = "", answerText = term),
        ),
    )

    @Test
    fun resumesFromFirstUnansweredItem() = runTest {
        val items = listOf(
            item("i1", "w1", answerIndex = 0, userAnswer = "a", isCorrect = true),
            item("i2", "w2", answerIndex = 1),
        )
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), items)
        val vm = makeVm(examRepo, wordRepo())
        val state = vm.uiState.value
        assertTrue(!state.loading)
        assertEquals(1, state.currentIndex)
        assertEquals(2, state.total)
    }

    @Test
    fun answer_persistsComputedCorrectness_andUpdatesScore() = runTest {
        val items = listOf(item("i1", "w1", answerIndex = 2), item("i2", "w2", answerIndex = 0))
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), items)
        val vm = makeVm(examRepo, wordRepo())

        vm.answer(selectedIndex = 2) // correct for i1

        val persisted = examRepo.items.first { it.id == "i1" }
        assertEquals(true, persisted.isCorrect)
        assertEquals("c", persisted.userAnswer) // options[2]
        assertTrue(vm.uiState.value.revealed)
        assertEquals(true, vm.uiState.value.lastCorrect)
        assertEquals(1, vm.uiState.value.score)
    }

    @Test
    fun finish_marksFinished_appliesSrsOncePerWord_idempotently() = runTest {
        // All items already answered -> constructing the VM auto-finishes (died-before-finish path).
        val items = listOf(
            item("i1", "w1", answerIndex = 0, userAnswer = "a", isCorrect = true),
            item("i2", "w2", answerIndex = 1, userAnswer = "a", isCorrect = false),
        )
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), items)
        val words = wordRepo()

        makeVm(examRepo, words)

        assertEquals(SessionState.FINISHED, examRepo.session.state)
        assertEquals(1, examRepo.session.score) // one correct

        // A second VM on the now-FINISHED session must NOT re-apply SRS.
        makeVm(examRepo, words)

        // Exactly one upsert per unique word across both runs (w1, w2) — never doubled.
        coVerify(exactly = 1) { words.upsert(match { it.id == "w1" }) }
        coVerify(exactly = 1) { words.upsert(match { it.id == "w2" }) }
    }

    @Test
    fun openSentence_okVerdict_countsCorrect_andExposesVerdict() = runTest {
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), listOf(openItem("i1", "w1")))
        val ai = mockk<AiClient>()
        coEvery { ai.checkOpenAnswer(any()) } returns
            Result.success(OpenAnswerVerdict(OpenAnswerVerdict.Verdict.OK, "Corrected.", "Nice!"))
        val vm = makeVm(examRepo, wordRepo(), ai)

        vm.checkOpenAnswer("I sat on the bank.")

        val persisted = examRepo.items.first { it.id == "i1" }
        assertEquals(true, persisted.isCorrect)
        assertEquals("I sat on the bank.", persisted.userAnswer)
        assertTrue(vm.uiState.value.revealed)
        assertEquals(1, vm.uiState.value.score)
        assertEquals(OpenAnswerVerdict.Verdict.OK, vm.uiState.value.openVerdict?.verdict)
    }

    @Test
    fun openSentence_minorVerdict_countsCorrect() = runTest {
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), listOf(openItem("i1", "w1")))
        val ai = mockk<AiClient>()
        coEvery { ai.checkOpenAnswer(any()) } returns
            Result.success(OpenAnswerVerdict(OpenAnswerVerdict.Verdict.MINOR, "Corrected.", "Almost!"))
        val vm = makeVm(examRepo, wordRepo(), ai)

        vm.checkOpenAnswer("I sit on bank.")

        assertEquals(true, examRepo.items.first { it.id == "i1" }.isCorrect)
    }

    @Test
    fun openSentence_checkFailure_thenSkip_isNeitherCorrectNorWrong_andFinishes() = runTest {
        val examRepo = FakeExamRepository(session(SessionState.IN_PROGRESS), listOf(openItem("i1", "w1")))
        val ai = mockk<AiClient>()
        coEvery { ai.checkOpenAnswer(any()) } returns Result.failure(AiError.Network)
        val vm = makeVm(examRepo, wordRepo(), ai)

        vm.checkOpenAnswer("some sentence")
        assertTrue(vm.uiState.value.checkFailed)
        assertTrue(!vm.uiState.value.revealed) // nothing persisted yet

        vm.skipOpen()
        val persisted = examRepo.items.first { it.id == "i1" }
        assertNull(persisted.isCorrect) // ignored by SRS
        assertEquals("", persisted.userAnswer) // non-null so it never blocks finishing
        assertTrue(vm.uiState.value.revealed)

        vm.next() // only item -> finishes the session
        assertEquals(SessionState.FINISHED, examRepo.session.state)
        assertEquals(0, examRepo.session.score)
    }
}

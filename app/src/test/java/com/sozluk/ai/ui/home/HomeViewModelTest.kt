package com.sozluk.ai.ui.home

import app.cash.turbine.test
import com.sozluk.ai.domain.OfflineQueue
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.ai.WordArticle
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.usecase.AddWordUseCase
import com.sozluk.ai.ui.screens.home.AddState
import com.sozluk.ai.ui.screens.home.HomeEvent
import com.sozluk.ai.ui.screens.home.HomeViewModel
import com.sozluk.ai.util.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val wordRepository = mockk<WordRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository> {
        every { nativeLang } returns flowOf(NativeLang.RU)
    }
    private val aiClient = mockk<AiClient>()
    private val offlineQueue = mockk<OfflineQueue>(relaxed = true)
    private val examRepository = mockk<ExamRepository>(relaxed = true)

    private val article = WordArticle(
        term = "bank",
        explanationEn = "the land beside a river",
        translationRu = "берег",
        translationTk = "kenar",
    )

    private fun existingWord() = WordEntry(
        id = "existing-1",
        term = "bank",
        normalizedTerm = "bank",
        explanationEn = "e",
        translationRu = "берег",
        translationTk = "kenar",
        sourceType = SourceType.MANUAL,
        createdAt = 0L,
    )

    private fun buildViewModel(): HomeViewModel {
        every { wordRepository.observeAll(any()) } returns flowOf(emptyList())
        every { wordRepository.search(any()) } returns flowOf(emptyList())
        val addWord = AddWordUseCase(wordRepository, aiClient)
        return HomeViewModel(wordRepository, settingsRepository, addWord, offlineQueue, examRepository)
    }

    @Test
    fun addWord_happyPath_savesAndNavigates() = runTest {
        coEvery { wordRepository.findByNormalizedTerm("bank") } returns emptyList()
        coEvery { aiClient.explainWord("bank", null) } returns Result.success(article)
        coEvery { wordRepository.upsert(any()) } just Runs

        val vm = buildViewModel()
        vm.onQueryChange("bank")

        vm.eventFlow.test {
            vm.onAskAi()
            val event = awaitItem()
            assertTrue(event is HomeEvent.NavigateToWord)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { wordRepository.upsert(any()) }
    }

    @Test
    fun addWord_deduplicates_showsPromptWithoutCallingAi() = runTest {
        coEvery { wordRepository.findByNormalizedTerm("bank") } returns listOf(existingWord())

        val vm = buildViewModel()
        // Keep the WhileSubscribed uiState hot so it recomputes.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onQueryChange("bank")
        vm.onAskAi()
        advanceUntilIdle()

        assertEquals("existing-1", vm.uiState.value.dedup?.existingId)
        coVerify(exactly = 0) { aiClient.explainWord(any(), any()) }
    }

    @Test
    fun addWord_noApiKey_setsNoApiKeyState() = runTest {
        coEvery { wordRepository.findByNormalizedTerm("bank") } returns emptyList()
        coEvery { aiClient.explainWord("bank", null) } returns Result.failure(AiError.NoApiKey)

        val vm = buildViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onQueryChange("bank")
        vm.onAskAi()
        advanceUntilIdle()

        assertEquals(AddState.NoApiKey, vm.uiState.value.addState)
    }

    @Test
    fun addWord_networkError_enqueuesOffline() = runTest {
        coEvery { wordRepository.findByNormalizedTerm("bank") } returns emptyList()
        coEvery { aiClient.explainWord("bank", null) } returns Result.failure(AiError.Network)
        coEvery { offlineQueue.enqueue("bank", null) } just Runs

        val vm = buildViewModel()
        vm.onQueryChange("bank")

        vm.eventFlow.test {
            vm.onAskAi()
            val event = awaitItem()
            assertTrue(event is HomeEvent.Queued)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { offlineQueue.enqueue("bank", null) }
    }
}

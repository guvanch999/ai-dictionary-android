package com.sozluk.ai.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.OfflineQueue
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.WordStatus
import com.sozluk.ai.domain.model.translationsFor
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.repository.WordSort
import com.sozluk.ai.domain.usecase.AddWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the word list; [firstTranslation] already reflects the native-language order. */
data class WordListItem(
    val id: String,
    val term: String,
    val firstTranslation: String,
    val createdAt: Long,
    val isMastered: Boolean,
)

/** State of the AI add flow (spec F-1). */
sealed interface AddState {
    data object Idle : AddState
    data object Loading : AddState
    data class Error(val error: AiError) : AddState
    data object NoApiKey : AddState
}

/** Dedup dialog data — a word with this normalized term already exists (spec §4.3). */
data class DedupPrompt(val existingId: String, val term: String)

data class HomeUiState(
    val query: String = "",
    val sort: WordSort = WordSort.DATE,
    val items: List<WordListItem> = emptyList(),
    val showAskAi: Boolean = false,
    val addState: AddState = AddState.Idle,
    val dedup: DedupPrompt? = null,
    val wordsDueCount: Int = 0,
    val unfinishedSessionId: String? = null,
)

/** Review badge + resumable-exam banner data (spec §7.4, F-3.6), refreshed on screen resume. */
private data class ExamInfo(val dueCount: Int = 0, val unfinishedSessionId: String? = null)

/** One-shot events for the UI (navigation, snackbars). */
sealed interface HomeEvent {
    data class NavigateToWord(val id: String) : HomeEvent
    data class Queued(val term: String) : HomeEvent
    data class Deleted(val term: String) : HomeEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository,
    private val addWord: AddWordUseCase,
    private val offlineQueue: OfflineQueue,
    private val examRepository: ExamRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(WordSort.DATE)
    private val addState = MutableStateFlow<AddState>(AddState.Idle)
    private val dedup = MutableStateFlow<DedupPrompt?>(null)
    private val examInfo = MutableStateFlow(ExamInfo())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    private var lastAttempt: Pair<String, String?>? = null
    private var lastDeleted: WordEntry? = null

    // Instant local filtering: debounce typing, then observe or search (spec F-2.2).
    private val items: StateFlow<List<WordListItem>> =
        combine(query.debounce(SEARCH_DEBOUNCE_MS), sort, settingsRepository.nativeLang, ::Triple)
            .flatMapLatest { (q, order, lang) ->
                val source = if (q.isBlank()) {
                    wordRepository.observeAll(order)
                } else {
                    wordRepository.search(q)
                }
                source.map { list -> list.map { it.toListItem(lang) } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val baseState: Flow<HomeUiState> =
        combine(query, sort, items, addState, dedup) { q, order, list, add, dd ->
            HomeUiState(
                query = q,
                sort = order,
                items = list,
                showAskAi = q.isNotBlank() && list.isEmpty() && add != AddState.Loading,
                addState = add,
                dedup = dd,
            )
        }

    val uiState: StateFlow<HomeUiState> =
        combine(baseState, examInfo) { base, exam ->
            base.copy(
                wordsDueCount = exam.dueCount,
                unfinishedSessionId = exam.unfinishedSessionId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        refreshExamInfo()
    }

    /** Recompute the review badge + resumable-exam banner. Call when the screen resumes. */
    fun refreshExamInfo() {
        viewModelScope.launch {
            val due = wordRepository.getDueForReview(System.currentTimeMillis()).size
            val unfinished = examRepository.getUnfinishedSession()?.id
            examInfo.value = ExamInfo(dueCount = due, unfinishedSessionId = unfinished)
        }
    }

    fun onQueryChange(value: String) { query.value = value }

    fun onClearQuery() { query.value = "" }

    fun onSortChange(value: WordSort) { sort.value = value }

    /** "Ask AI" pressed: dedup first (spec §4.3), else add a new word. */
    fun onAskAi() {
        val term = query.value.trim()
        if (term.isEmpty()) return
        viewModelScope.launch {
            val existing = wordRepository.findByNormalizedTerm(term)
            if (existing.isNotEmpty()) {
                dedup.value = DedupPrompt(existing.first().id, term)
            } else {
                addNew(term, contextSentence = null)
            }
        }
    }

    fun onDedupOpenExisting() {
        val prompt = dedup.value ?: return
        dedup.value = null
        viewModelScope.launch { events.send(HomeEvent.NavigateToWord(prompt.existingId)) }
    }

    fun onDedupAskAgain() {
        val prompt = dedup.value ?: return
        dedup.value = null
        viewModelScope.launch { addNew(prompt.term, contextSentence = null) }
    }

    fun onDedupDismiss() { dedup.value = null }

    fun onRetryAdd() {
        val (term, ctx) = lastAttempt ?: return
        viewModelScope.launch { addNew(term, ctx) }
    }

    fun onDismissAddState() { addState.value = AddState.Idle }

    private suspend fun addNew(term: String, contextSentence: String?) {
        lastAttempt = term to contextSentence
        addState.value = AddState.Loading
        addWord(term, contextSentence, SourceType.MANUAL)
            .onSuccess { entry ->
                addState.value = AddState.Idle
                query.value = ""
                events.send(HomeEvent.NavigateToWord(entry.id))
            }
            .onFailure { error ->
                when (error) {
                    is AiError.NoApiKey -> addState.value = AddState.NoApiKey
                    is AiError.Network -> {
                        offlineQueue.enqueue(term, contextSentence)
                        addState.value = AddState.Idle
                        query.value = ""
                        events.send(HomeEvent.Queued(term))
                    }
                    is AiError -> addState.value = AddState.Error(error)
                    else -> addState.value = AddState.Error(AiError.Unknown(error.message ?: ""))
                }
            }
    }

    fun onDelete(item: WordListItem) {
        viewModelScope.launch {
            lastDeleted = wordRepository.getById(item.id)
            wordRepository.deleteById(item.id)
            events.send(HomeEvent.Deleted(item.term))
        }
    }

    fun onUndoDelete() {
        val word = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { wordRepository.upsert(word) }
    }

    private fun WordEntry.toListItem(lang: NativeLang) = WordListItem(
        id = id,
        term = term,
        firstTranslation = translationsFor(lang).first,
        createdAt = createdAt,
        isMastered = status == WordStatus.MASTERED,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

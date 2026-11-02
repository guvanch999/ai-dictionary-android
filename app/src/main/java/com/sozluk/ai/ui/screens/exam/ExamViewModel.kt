package com.sozluk.ai.ui.screens.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.OpenAnswerRequest
import com.sozluk.ai.domain.ai.OpenAnswerVerdict
import com.sozluk.ai.domain.ai.WordArticle
import com.sozluk.ai.domain.ai.isCorrect
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.model.QuizItem
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.aggregateResults
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.quiz.scoreOf
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.srs.SrsUpdater
import com.sozluk.ai.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One question on screen: the persisted [item] plus its parsed [payload]. */
data class ExamQuestion(
    val item: QuizItem,
    val payload: LocalQuizPayload,
)

data class ExamUiState(
    val loading: Boolean = true,
    val questions: List<ExamQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val revealed: Boolean = false,
    val lastCorrect: Boolean? = null,
    val score: Int = 0,
    val total: Int = 0,
    val offline: Boolean = false,
    val showExitDialog: Boolean = false,
    // OPEN_SENTENCE grading (F-4): a check is in flight, its verdict, or a failed check offering skip.
    val checking: Boolean = false,
    val openVerdict: OpenAnswerVerdict? = null,
    val checkFailed: Boolean = false,
) {
    val current: ExamQuestion? get() = questions.getOrNull(currentIndex)
    val isLast: Boolean get() = currentIndex >= questions.lastIndex
    val answered: Int get() = questions.count { it.item.userAnswer != null }
}

/**
 * Drives one exam session (spec F-3.3). Every answer is persisted immediately via
 * [ExamRepository.updateItem] so the session survives process death and resumes from the first
 * unanswered question. On finish the session is marked FINISHED and SRS is applied once per word.
 */
@HiltViewModel
class ExamViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val examRepository: ExamRepository,
    private val wordRepository: WordRepository,
    private val srsUpdater: SrsUpdater,
    private val aiClient: AiClient,
    private val interfaceLocaleProvider: InterfaceLocaleProvider,
) : ViewModel() {

    // Navigation URL-decodes the path argument.
    private val sessionId: String = savedStateHandle.get<String>(Routes.ARG_SESSION_ID).orEmpty()

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val finished = Channel<Unit>(Channel.BUFFERED)
    /** Emits once the session is finished so the screen can navigate to results. */
    val finishedFlow: Flow<Unit> = finished.receiveAsFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val session = examRepository.getSession(sessionId)
            if (session == null) {
                _uiState.value = ExamUiState(loading = false)
                return@launch
            }
            val items = examRepository.getItemsForSession(sessionId)
            val questions = items.mapNotNull { item ->
                val payload = parsePayload(item)
                if (payload == null) {
                    // Unparseable payload: mark wrong immediately so it can never block the finish.
                    if (item.userAnswer == null) examRepository.updateItem(item.copy(userAnswer = "", isCorrect = false))
                    null
                } else {
                    ExamQuestion(item, payload)
                }
            }

            if (session.state == SessionState.FINISHED) {
                emitFinished(session.score, questions.size)
                return@launch
            }
            if (questions.isEmpty()) {
                _uiState.value = ExamUiState(loading = false)
                return@launch
            }

            val firstUnanswered = questions.indexOfFirst { it.item.userAnswer == null }
            if (firstUnanswered == -1) {
                // Every item was answered but the session died before finishing — finalize now.
                finish()
                return@launch
            }

            _uiState.value = ExamUiState(
                loading = false,
                questions = questions,
                currentIndex = firstUnanswered,
                total = questions.size,
                score = questions.count { it.item.isCorrect == true },
                offline = questions.none { it.item.type.name.startsWith("AI_") },
            )
        }
    }

    /** Answer the current question. [selectedIndex] for choice types, [typedText] for scramble. */
    fun answer(selectedIndex: Int? = null, typedText: String? = null) {
        val state = _uiState.value
        val q = state.current ?: return
        if (q.item.userAnswer != null || state.revealed) return

        val correct = when (q.item.type) {
            QuizType.LETTER_SCRAMBLE -> normalize(typedText) == normalize(q.payload.answerText)
            else -> selectedIndex != null && selectedIndex == q.payload.answerIndex
        }
        val userAnswer = typedText ?: selectedIndex?.let { q.payload.options.getOrNull(it) } ?: ""
        val updatedItem = q.item.copy(userAnswer = userAnswer, isCorrect = correct)

        viewModelScope.launch {
            examRepository.updateItem(updatedItem) // persist immediately (survives process death)
            val questions = _uiState.value.questions.toMutableList()
            questions[_uiState.value.currentIndex] = q.copy(item = updatedItem)
            _uiState.value = _uiState.value.copy(
                questions = questions,
                revealed = true,
                lastCorrect = correct,
                score = questions.count { it.item.isCorrect == true },
            )
        }
    }

    /**
     * Grade an OPEN_SENTENCE answer (F-4) via the AI. OK/MINOR count as correct, WRONG as an error
     * (spec §6.3). A network/API failure does not persist anything — the screen then offers
     * [skipOpen]. Runs only for the current OPEN_SENTENCE question.
     */
    fun checkOpenAnswer(sentence: String) {
        val state = _uiState.value
        val q = state.current ?: return
        if (q.item.type != QuizType.OPEN_SENTENCE) return
        if (q.item.userAnswer != null || state.revealed || state.checking) return
        if (sentence.isBlank()) return

        _uiState.value = state.copy(checking = true, checkFailed = false)
        viewModelScope.launch {
            val word = wordRepository.getById(q.item.wordId)
            if (word == null) {
                _uiState.value = _uiState.value.copy(checking = false, checkFailed = true)
                return@launch
            }
            val request = OpenAnswerRequest(
                term = word.term,
                article = word.toArticle(),
                userSentence = sentence,
                uiLang = interfaceLocaleProvider.current(),
            )
            aiClient.checkOpenAnswer(request).fold(
                onSuccess = { verdict ->
                    val correct = verdict.verdict.isCorrect()
                    val updatedItem = q.item.copy(userAnswer = sentence, isCorrect = correct)
                    examRepository.updateItem(updatedItem)
                    val questions = _uiState.value.questions.toMutableList()
                    questions[_uiState.value.currentIndex] = q.copy(item = updatedItem)
                    _uiState.value = _uiState.value.copy(
                        questions = questions,
                        checking = false,
                        revealed = true,
                        lastCorrect = correct,
                        openVerdict = verdict,
                        score = questions.count { it.item.isCorrect == true },
                    )
                },
                onFailure = {
                    // Nothing persisted — the item stays unanswered so the learner can skip it.
                    _uiState.value = _uiState.value.copy(checking = false, checkFailed = true)
                },
            )
        }
    }

    /**
     * Skip the current OPEN_SENTENCE after a failed check (spec F-4): persist a non-null answer with
     * a null result so it counts as neither correct nor wrong (ignored by SRS) and never blocks
     * finishing or resuming.
     */
    fun skipOpen() {
        val state = _uiState.value
        val q = state.current ?: return
        if (q.item.type != QuizType.OPEN_SENTENCE || q.item.userAnswer != null) return

        val updatedItem = q.item.copy(userAnswer = "", isCorrect = null)
        viewModelScope.launch {
            examRepository.updateItem(updatedItem)
            val questions = _uiState.value.questions.toMutableList()
            questions[_uiState.value.currentIndex] = q.copy(item = updatedItem)
            _uiState.value = _uiState.value.copy(
                questions = questions,
                checking = false,
                checkFailed = false,
                revealed = true,
                lastCorrect = null,
            )
        }
    }

    fun next() {
        val state = _uiState.value
        if (!state.revealed) return
        if (state.isLast) {
            viewModelScope.launch { finish() }
        } else {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                revealed = false,
                lastCorrect = null,
                checking = false,
                openVerdict = null,
                checkFailed = false,
            )
        }
    }

    fun requestExit() { _uiState.value = _uiState.value.copy(showExitDialog = true) }

    fun dismissExit() { _uiState.value = _uiState.value.copy(showExitDialog = false) }

    /**
     * Finalize the session: mark FINISHED then apply SRS per unique word. Idempotent — a session
     * already FINISHED skips SRS (SrsUpdater is NOT idempotent). The FINISHED marker is committed
     * before SRS so a crash mid-way loses (best-effort) stats rather than double-applying them.
     */
    private suspend fun finish() {
        val session = examRepository.getSession(sessionId) ?: return
        val items = examRepository.getItemsForSession(sessionId)
        if (session.state == SessionState.FINISHED) {
            emitFinished(session.score, items.size)
            return
        }
        val score = scoreOf(items)
        examRepository.updateSession(session.copy(state = SessionState.FINISHED, score = score))
        aggregateResults(items).forEach { (wordId, results) ->
            val word = wordRepository.getById(wordId) ?: return@forEach
            wordRepository.upsert(srsUpdater.apply(word, results))
        }
        emitFinished(score, items.size)
    }

    private suspend fun emitFinished(score: Int, total: Int) {
        _uiState.value = _uiState.value.copy(loading = false, score = score, total = total)
        finished.send(Unit)
    }

    private fun parsePayload(item: QuizItem): LocalQuizPayload? =
        try {
            quizJson.decodeFromString(LocalQuizPayload.serializer(), item.payloadJson)
        } catch (e: Exception) {
            null
        }

    private fun normalize(text: String?): String =
        text.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}

/** The stored word as an AI article, to give the open-answer grader the term's meaning (F-4). */
private fun WordEntry.toArticle(): WordArticle = WordArticle(
    term = term,
    partOfSpeech = partOfSpeech,
    ipa = ipa,
    explanationEn = explanationEn,
    translationRu = translationRu,
    translationTk = translationTk,
    synonyms = synonyms,
    examples = examples,
    commonMistake = commonMistake,
)

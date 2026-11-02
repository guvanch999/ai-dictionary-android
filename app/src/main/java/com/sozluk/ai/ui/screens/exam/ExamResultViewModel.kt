package com.sozluk.ai.ui.screens.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.quiz.LocalQuizPayload
import com.sozluk.ai.domain.quiz.quizJson
import com.sozluk.ai.domain.repository.ExamRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One wrong answer in the result review (spec F-3.4), linking back to the word card. */
data class ExamMistake(
    val wordId: String,
    val term: String,
    val question: String,
    val yourAnswer: String,
    val correctAnswer: String,
)

data class ExamResultUiState(
    val loading: Boolean = true,
    val score: Int = 0,
    val total: Int = 0,
    val mistakes: List<ExamMistake> = emptyList(),
) {
    val percent: Int get() = if (total == 0) 0 else (score * 100) / total
}

@HiltViewModel
class ExamResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val examRepository: ExamRepository,
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Routes.ARG_SESSION_ID).orEmpty()

    private val _uiState = MutableStateFlow(ExamResultUiState())
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val session = examRepository.getSession(sessionId)
            val items = examRepository.getItemsForSession(sessionId)
            val mistakes = items.filter { it.isCorrect == false }.mapNotNull { item ->
                val payload = parsePayload(item) ?: return@mapNotNull null
                val term = wordRepository.getById(item.wordId)?.term ?: return@mapNotNull null
                ExamMistake(
                    wordId = item.wordId,
                    term = term,
                    question = payload.prompt.ifBlank { term },
                    yourAnswer = item.userAnswer.orEmpty(),
                    correctAnswer = payload.answerText,
                )
            }
            _uiState.value = ExamResultUiState(
                loading = false,
                score = session?.score ?: items.count { it.isCorrect == true },
                total = session?.total ?: items.size,
                mistakes = mistakes,
            )
        }
    }

    private fun parsePayload(item: com.sozluk.ai.domain.model.QuizItem): LocalQuizPayload? =
        try {
            quizJson.decodeFromString(LocalQuizPayload.serializer(), item.payloadJson)
        } catch (e: Exception) {
            null
        }
}

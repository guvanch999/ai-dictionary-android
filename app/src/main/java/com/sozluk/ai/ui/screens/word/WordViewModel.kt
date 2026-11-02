package com.sozluk.ai.ui.screens.word

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.ui.common.TtsSpeaker
import com.sozluk.ai.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordUiState(
    val loading: Boolean = true,
    val word: WordEntry? = null,
    val nativeLang: NativeLang = NativeLang.RU,
    val refreshing: Boolean = false,
    val error: AiError? = null,
    val ttsAvailable: Boolean = false,
)

@HiltViewModel
class WordViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val wordRepository: WordRepository,
    private val aiClient: AiClient,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Created when the word screen opens (lazy relative to app start); freed in onCleared.
    private val tts = TtsSpeaker(context)

    // Navigation already URL-decodes path arguments.
    private val wordId: String = savedStateHandle.get<String>(Routes.ARG_WORD_ID).orEmpty()

    private val word = MutableStateFlow<WordEntry?>(null)
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<AiError?>(null)

    private val closed = Channel<Unit>(Channel.BUFFERED)
    /** Emits when the word was deleted and the screen should close. */
    val closedFlow: Flow<Unit> = closed.receiveAsFlow()

    val uiState: StateFlow<WordUiState> =
        combine(word, loading, refreshing, error, settingsRepository.nativeLang) { w, l, r, e, lang ->
            WordUiState(loading = l, word = w, nativeLang = lang, refreshing = r, error = e)
        }.combine(tts.available) { state, ttsOk -> state.copy(ttsAvailable = ttsOk) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordUiState())

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            loading.value = true
            word.value = wordRepository.getById(wordId)
            loading.value = false
        }
    }

    fun delete() {
        viewModelScope.launch {
            wordRepository.deleteById(wordId)
            closed.send(Unit)
        }
    }

    /** Re-query AI with the same term + context and overwrite the article, keeping SRS state (spec F-2.5). */
    fun refreshViaAi() {
        val current = word.value ?: return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            aiClient.explainWord(current.term, current.contextSentence)
                .onSuccess { article ->
                    val updated = current.copy(
                        explanationEn = article.explanationEn,
                        translationRu = article.translationRu,
                        translationTk = article.translationTk,
                        partOfSpeech = article.partOfSpeech,
                        ipa = article.ipa,
                        synonyms = article.synonyms,
                        examples = article.examples,
                        commonMistake = article.commonMistake,
                    )
                    wordRepository.upsert(updated)
                    word.value = updated
                }
                .onFailure { e ->
                    error.value = e as? AiError ?: AiError.Unknown(e.message ?: "")
                }
            refreshing.value = false
        }
    }

    fun saveEdits(explanationEn: String, translationRu: String, translationTk: String) {
        val current = word.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                explanationEn = explanationEn.trim(),
                translationRu = translationRu.trim(),
                translationTk = translationTk.trim(),
            )
            wordRepository.upsert(updated)
            word.value = updated
        }
    }

    fun speak(text: String) = tts.speak(text)

    fun dismissError() { error.value = null }

    override fun onCleared() {
        tts.release()
        super.onCleared()
    }
}

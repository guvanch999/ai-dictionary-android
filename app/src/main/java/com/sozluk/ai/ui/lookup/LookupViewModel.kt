package com.sozluk.ai.ui.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.text.TextSelection
import com.sozluk.ai.domain.usecase.AddWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state of the PROCESS_TEXT lookup card (spec F-5). */
sealed interface LookupUiState {
    /** Selection had no usable word — the host activity closes. */
    data object Empty : LookupUiState

    /** More than two words: pick which one to look up; the whole selection is the context. */
    data class SentencePicker(val words: List<String>, val sentence: String) : LookupUiState

    data object Loading : LookupUiState
    data class Saved(val entry: WordEntry, val nativeLang: NativeLang) : LookupUiState
    data class Error(val error: AiError) : LookupUiState
    data object NoApiKey : LookupUiState
}

@HiltViewModel
class LookupViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val addWord: AddWordUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LookupUiState>(LookupUiState.Loading)
    val uiState: StateFlow<LookupUiState> = _state.asStateFlow()

    private var nativeLang: NativeLang = NativeLang.RU
    private var sentence: String? = null
    private var lastAttempt: Pair<String, String?>? = null
    private var started = false

    init {
        viewModelScope.launch { settingsRepository.nativeLang.collect { nativeLang = it } }
    }

    /** Called once with the selected text; decides direct lookup vs. sentence picker. */
    fun start(selection: String) {
        if (started) return
        started = true
        val words = TextSelection.tokenize(selection)
        when {
            words.isEmpty() -> _state.value = LookupUiState.Empty
            words.size <= 2 -> lookup(term = selection.trim(), contextSentence = null)
            else -> {
                sentence = selection.trim()
                _state.value = LookupUiState.SentencePicker(words, selection.trim())
            }
        }
    }

    /** A word tapped inside the sentence picker: it becomes the term, the sentence the context. */
    fun onWordSelected(word: String) = lookup(term = word, contextSentence = sentence)

    fun onRetry() {
        val (term, ctx) = lastAttempt ?: return
        lookup(term, ctx)
    }

    private fun lookup(term: String, contextSentence: String?) {
        lastAttempt = term to contextSentence
        _state.value = LookupUiState.Loading
        viewModelScope.launch {
            // Already saved → show instantly, no AI call (spec F-5 DoD).
            val existing = wordRepository.findByNormalizedTerm(term.trim())
            if (existing.isNotEmpty()) {
                _state.value = LookupUiState.Saved(existing.first(), nativeLang)
                return@launch
            }
            addWord(term, contextSentence, SourceType.PROCESS_TEXT)
                .onSuccess { entry -> _state.value = LookupUiState.Saved(entry, nativeLang) }
                .onFailure { error ->
                    _state.value = when (error) {
                        is AiError.NoApiKey -> LookupUiState.NoApiKey
                        is AiError -> LookupUiState.Error(error)
                        else -> LookupUiState.Error(AiError.Unknown(error.message ?: ""))
                    }
                }
        }
    }
}

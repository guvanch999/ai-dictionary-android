package com.sozluk.ai.ui.screens.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.domain.quiz.ExamComposer
import com.sozluk.ai.domain.srs.ExamScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Composes an exam session from the chosen settings (spec F-3.1). AI generation happens inside
 * [ExamComposer.compose], so [preparing] drives the Start button's progress indicator.
 */
@HiltViewModel
class ExamSetupViewModel @Inject constructor(
    private val examComposer: ExamComposer,
) : ViewModel() {

    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val started = Channel<String>(Channel.BUFFERED)
    /** Emits the new session id once composition finishes. */
    val startedFlow: Flow<String> = started.receiveAsFlow()

    fun start(count: Int, scope: ExamScope, includeMastered: Boolean) {
        if (_preparing.value) return
        _preparing.value = true
        viewModelScope.launch {
            try {
                val session = examComposer.compose(
                    count = count,
                    scope = scope,
                    includeMastered = includeMastered,
                    mode = scope.name,
                )
                started.send(session.id)
            } finally {
                _preparing.value = false
            }
        }
    }
}

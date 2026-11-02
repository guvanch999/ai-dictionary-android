package com.sozluk.ai.ui.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.translationsFor
import com.sozluk.ai.ui.common.NoApiKeyDialog
import com.sozluk.ai.ui.common.aiErrorMessage

/**
 * Compact lookup card shown over the source app for a PROCESS_TEXT selection (spec F-5).
 * Tapping the scrim (or NoApiKey dialog) dismisses; the card consumes its own taps.
 */
@Composable
fun LookupScreen(
    selection: String,
    onOpenInApp: (String) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: LookupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selection) { viewModel.start(selection) }
    LaunchedEffect(state) { if (state is LookupUiState.Empty) onDismiss() }

    when (val s = state) {
        is LookupUiState.Empty -> Unit // handled by the effect above
        is LookupUiState.NoApiKey -> NoApiKeyDialog(
            onOpenSettings = onOpenApp,
            onDismiss = onDismiss,
        )
        else -> Scrim(onDismiss = onDismiss) {
            LookupCard(
                state = s,
                onWordSelected = viewModel::onWordSelected,
                onRetry = viewModel::onRetry,
                onOpenInApp = onOpenInApp,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Card consumes taps so touching it does not close the overlay.
        Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures {} }) {
            content()
        }
    }
}

@Composable
private fun LookupCard(
    state: LookupUiState,
    onWordSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenInApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is LookupUiState.Loading -> LoadingBody()
                is LookupUiState.SentencePicker -> SentencePickerBody(state, onWordSelected)
                is LookupUiState.Saved -> SavedBody(state, onOpenInApp, onDismiss)
                is LookupUiState.Error -> ErrorBody(state, onRetry, onDismiss)
                else -> Unit
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.lookup_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentencePickerBody(state: LookupUiState.SentencePicker, onWordSelected: (String) -> Unit) {
    Text(stringResource(R.string.lookup_sentence_hint), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.words.forEach { word ->
            AssistChip(onClick = { onWordSelected(word) }, label = { Text(word) })
        }
    }
}

@Composable
private fun SavedBody(state: LookupUiState.Saved, onOpenInApp: (String) -> Unit, onDismiss: () -> Unit) {
    val word: WordEntry = state.entry
    Text(word.term, style = MaterialTheme.typography.headlineSmall)

    val (first, second) = word.translationsFor(state.nativeLang)
    Text(first, style = MaterialTheme.typography.titleMedium)
    if (second.isNotBlank()) {
        Text(second, style = MaterialTheme.typography.bodyMedium)
    }
    if (word.explanationEn.isNotBlank()) {
        Text(word.explanationEn, style = MaterialTheme.typography.bodyMedium)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.lookup_close)) }
        TextButton(onClick = { onOpenInApp(word.id) }) {
            Text(stringResource(R.string.lookup_open_in_app))
        }
    }
}

@Composable
private fun ErrorBody(state: LookupUiState.Error, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Text(stringResource(R.string.add_error_title), style = MaterialTheme.typography.titleMedium)
    Text(aiErrorMessage(state.error), style = MaterialTheme.typography.bodyMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.lookup_close)) }
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

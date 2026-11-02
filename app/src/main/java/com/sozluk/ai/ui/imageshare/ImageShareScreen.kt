package com.sozluk.ai.ui.imageshare

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Host composable for the shared-image flow (spec F-6): decode → annotate/crop → OCR cascade →
 * save. Mirrors the PROCESS_TEXT lookup states, but full-screen since it owns its own activity.
 */
@Composable
fun ImageShareRoot(
    uri: Uri,
    onOpenWord: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ImageShareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uri) { viewModel.load(uri) }

    when (val s = state) {
        is ImageShareUiState.Annotating -> AnnotateScreen(
            bitmap = s.bitmap,
            onConfirm = viewModel::onCropConfirmed,
            onCancel = onDismiss,
        )
        is ImageShareUiState.NoApiKey -> NoApiKeyDialog(
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss,
        )
        else -> Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (s) {
                    is ImageShareUiState.Loading,
                    is ImageShareUiState.Processing -> CircularProgressIndicator()
                    is ImageShareUiState.WordPicker -> WordPickerBody(s, viewModel::onWordSelected)
                    is ImageShareUiState.Saved -> SavedBody(s, onOpenWord, onDismiss)
                    is ImageShareUiState.Error -> ErrorBody(
                        s,
                        onRetry = viewModel::onRetry,
                        onBackToEditor = viewModel::onBackToEditor,
                        onDismiss = onDismiss,
                    )
                    is ImageShareUiState.Fatal -> FatalBody(s, onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordPickerBody(state: ImageShareUiState.WordPicker, onWordSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ocr_word_picker_hint), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.words.forEach { word ->
                AssistChip(onClick = { onWordSelected(word) }, label = { Text(word) })
            }
        }
    }
}

@Composable
private fun SavedBody(state: ImageShareUiState.Saved, onOpenWord: (String) -> Unit, onDismiss: () -> Unit) {
    val word: WordEntry = state.entry
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word.term, style = MaterialTheme.typography.headlineSmall)
        val (first, second) = word.translationsFor(state.nativeLang)
        Text(first, style = MaterialTheme.typography.titleMedium)
        if (second.isNotBlank()) Text(second, style = MaterialTheme.typography.bodyMedium)
        if (word.explanationEn.isNotBlank()) {
            Text(word.explanationEn, style = MaterialTheme.typography.bodyMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lookup_close)) }
            TextButton(onClick = { onOpenWord(word.id) }) {
                Text(stringResource(R.string.lookup_open_in_app))
            }
        }
    }
}

@Composable
private fun ErrorBody(
    state: ImageShareUiState.Error,
    onRetry: () -> Unit,
    onBackToEditor: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_error_title), style = MaterialTheme.typography.titleMedium)
        Text(aiErrorMessage(state.error), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lookup_close)) }
            TextButton(onClick = onBackToEditor) { Text(stringResource(R.string.annotate_back_to_editor)) }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun FatalBody(state: ImageShareUiState.Fatal, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(state.messageRes), style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lookup_close)) }
        }
    }
}

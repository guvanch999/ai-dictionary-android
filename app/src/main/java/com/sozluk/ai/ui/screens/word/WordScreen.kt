package com.sozluk.ai.ui.screens.word

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.translationFor
import com.sozluk.ai.domain.model.translationsFor
import com.sozluk.ai.ui.common.aiErrorMessage
import com.sozluk.ai.ui.common.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordScreen(
    wordId: String,
    onBack: () -> Unit,
    viewModel: WordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.closedFlow.collect { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.word?.term ?: stringResource(R.string.word_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val term = state.word?.term
                    if (term != null) {
                        if (state.ttsAvailable) {
                            IconButton(onClick = { viewModel.speak(term) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.word_speak_term),
                                )
                            }
                        }
                        IconButton(onClick = viewModel::refreshViaAi, enabled = !state.refreshing) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.word_refresh),
                            )
                        }
                        IconButton(onClick = { showEdit = true }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.word_edit),
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.word_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val word = state.word
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                word == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(stringResource(R.string.word_not_found))
                }
                else -> WordContent(
                    word = word,
                    nativeLang = state.nativeLang,
                    ttsAvailable = state.ttsAvailable,
                    onSpeak = viewModel::speak,
                )
            }

            if (state.refreshing) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.word_delete_confirm_title)) },
            text = { Text(stringResource(R.string.word_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.word_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val editing = state.word
    if (showEdit && editing != null) {
        EditDialog(
            word = editing,
            onSave = { expl, ru, tk ->
                viewModel.saveEdits(expl, ru, tk)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.add_error_title)) },
            text = { Text(aiErrorMessage(error)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordContent(
    word: WordEntry,
    nativeLang: NativeLang,
    ttsAvailable: Boolean,
    onSpeak: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Screenshot crop thumbnail, if this word came from an image (spec F-6, step 5).
        word.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
            CropThumbnail(path)
        }

        // Header: part of speech + IPA.
        val header = listOfNotNull(word.partOfSpeech, word.ipa).joinToString("  ·  ")
        if (header.isNotBlank()) {
            Text(header, style = MaterialTheme.typography.bodyMedium)
        }

        Section(title = stringResource(R.string.word_explanation_label)) {
            Text(word.explanationEn, style = MaterialTheme.typography.bodyLarge)
        }

        // Translations, native language first (spec §8.2).
        val (first, second) = word.translationsFor(nativeLang)
        val (firstLabelRes, secondLabelRes) = when (nativeLang) {
            NativeLang.RU -> R.string.lang_ru to R.string.lang_tk
            NativeLang.TK -> R.string.lang_tk to R.string.lang_ru
        }
        Section(title = stringResource(R.string.word_translations_label)) {
            Text("${stringResource(firstLabelRes)}: $first", style = MaterialTheme.typography.bodyLarge)
            Text("${stringResource(secondLabelRes)}: $second", style = MaterialTheme.typography.bodyMedium)
        }

        if (word.examples.isNotEmpty()) {
            Section(title = stringResource(R.string.word_examples_label)) {
                word.examples.forEach { ex ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            ex.en,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (ttsAvailable) {
                            IconButton(onClick = { onSpeak(ex.en) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.word_speak_example),
                                )
                            }
                        }
                    }
                    Text(ex.translationFor(nativeLang), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (word.synonyms.isNotEmpty()) {
            Section(title = stringResource(R.string.word_synonyms_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    word.synonyms.forEach { syn ->
                        AssistChip(onClick = {}, label = { Text(syn) })
                    }
                }
            }
        }

        word.commonMistake?.takeIf { it.isNotBlank() }?.let {
            Section(title = stringResource(R.string.word_common_mistake_label)) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        word.contextSentence?.takeIf { it.isNotBlank() }?.let {
            Section(title = stringResource(R.string.word_context_label)) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            text = formatDate(word.createdAt),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Loads and shows the stored crop for a screenshot word; decode happens off the main thread. */
@Composable
private fun CropThumbnail(path: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = stringResource(R.string.word_image_thumbnail),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun EditDialog(
    word: WordEntry,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var explanation by remember { mutableStateOf(word.explanationEn) }
    var ru by remember { mutableStateOf(word.translationRu) }
    var tk by remember { mutableStateOf(word.translationTk) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text(stringResource(R.string.word_edit_explanation)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ru,
                    onValueChange = { ru = it },
                    label = { Text(stringResource(R.string.word_edit_translation_ru)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tk,
                    onValueChange = { tk = it },
                    label = { Text(stringResource(R.string.word_edit_translation_tk)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(explanation, ru, tk) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

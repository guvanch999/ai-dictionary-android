package com.sozluk.ai.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.repository.WordSort
import com.sozluk.ai.ui.common.AddErrorDialog
import com.sozluk.ai.ui.common.NoApiKeyDialog
import com.sozluk.ai.ui.common.aiErrorMessage
import com.sozluk.ai.ui.common.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWord: (String) -> Unit,
    onOpenExamSetup: (String) -> Unit,
    onContinueExam: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recompute the review badge + resumable-exam banner whenever Home comes back to the front.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshExamInfo()
    }

    val queuedMsg = stringResource(R.string.home_added_to_queue)
    val deletedMsg = stringResource(R.string.home_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.NavigateToWord -> onOpenWord(event.id)
                is HomeEvent.Queued ->
                    snackbarHostState.showSnackbar(String.format(queuedMsg, event.term))
                is HomeEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = String.format(deletedMsg, event.term),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    SortMenu(current = state.sort, onSelect = viewModel::onSortChange)
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.home_open_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ExamEntry(
                    unfinishedSessionId = state.unfinishedSessionId,
                    wordsDueCount = state.wordsDueCount,
                    onContinueExam = onContinueExam,
                    onStartDueExam = { onOpenExamSetup("DUE") },
                )

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::onClearQuery) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                )
                            }
                        }
                    },
                )

                if (state.showAskAi) {
                    AskAiButton(term = state.query.trim(), onClick = viewModel::onAskAi)
                }

                when {
                    state.items.isEmpty() && state.query.isBlank() -> EmptyState()
                    else -> WordList(
                        items = state.items,
                        onOpen = onOpenWord,
                        onDelete = viewModel::onDelete,
                    )
                }
            }

            if (state.addState == AddState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    state.dedup?.let { prompt ->
        DedupDialog(
            term = prompt.term,
            onOpenSaved = viewModel::onDedupOpenExisting,
            onAskAgain = viewModel::onDedupAskAgain,
            onDismiss = viewModel::onDedupDismiss,
        )
    }

    when (val add = state.addState) {
        is AddState.NoApiKey -> NoApiKeyDialog(
            onOpenSettings = {
                viewModel.onDismissAddState()
                onOpenSettings()
            },
            onDismiss = viewModel::onDismissAddState,
        )
        is AddState.Error -> AddErrorDialog(
            message = aiErrorMessage(add.error),
            onRetry = viewModel::onRetryAdd,
            onDismiss = viewModel::onDismissAddState,
        )
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenu(current: WordSort, onSelect: (WordSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_sort_label))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortMenuItem(R.string.home_sort_date, WordSort.DATE, current) { onSelect(it); expanded = false }
        SortMenuItem(R.string.home_sort_alphabet, WordSort.ALPHABETICAL, current) { onSelect(it); expanded = false }
        SortMenuItem(R.string.home_sort_weakest, WordSort.WEAKNESS, current) { onSelect(it); expanded = false }
    }
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    sort: WordSort,
    current: WordSort,
    onSelect: (WordSort) -> Unit,
) {
    DropdownMenuItem(
        text = {
            val prefix = if (sort == current) "• " else ""
            Text(prefix + stringResource(labelRes))
        },
        onClick = { onSelect(sort) },
    )
}

@Composable
private fun ExamEntry(
    unfinishedSessionId: String?,
    wordsDueCount: Int,
    onContinueExam: (String) -> Unit,
    onStartDueExam: () -> Unit,
) {
    if (unfinishedSessionId == null && wordsDueCount <= 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (unfinishedSessionId != null) {
            androidx.compose.material3.Button(
                onClick = { onContinueExam(unfinishedSessionId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_continue_exam))
            }
        }
        if (wordsDueCount > 0) {
            androidx.compose.material3.OutlinedButton(
                onClick = onStartDueExam,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pluralStringResource(R.plurals.home_words_due, wordsDueCount, wordsDueCount))
            }
        }
    }
}

@Composable
private fun AskAiButton(term: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(String.format(stringResource(R.string.home_ask_ai), term))
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordList(
    items: List<WordListItem>,
    onOpen: (String) -> Unit,
    onDelete: (WordListItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onDelete(item)
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                },
            ) {
                WordRow(item = item, onClick = { onOpen(item.id) })
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun WordRow(item: WordListItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val title = if (item.isMastered) "${item.term}  ✓" else item.term
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = item.firstTranslation,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDate(item.createdAt),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DedupDialog(
    term: String,
    onOpenSaved: () -> Unit,
    onAskAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dedup_title)) },
        text = { Text(String.format(stringResource(R.string.dedup_message), term)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onOpenSaved) {
                Text(stringResource(R.string.dedup_open_saved))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onAskAgain) {
                Text(stringResource(R.string.dedup_ask_again))
            }
        },
    )
}


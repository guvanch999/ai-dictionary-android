package com.sozluk.ai.ui.screens.exam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.ai.OpenAnswerVerdict
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.ui.common.TtsSpeaker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    sessionId: String,
    onFinish: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val speak = rememberTts()

    LaunchedEffect(Unit) {
        viewModel.finishedFlow.collect { onFinish(sessionId) }
    }

    val offlineMessage = stringResource(R.string.exam_offline)
    LaunchedEffect(state.offline) {
        if (state.offline) snackbarHost.showSnackbar(offlineMessage)
    }

    // System back opens the same confirm dialog as the top-bar arrow.
    BackHandler(enabled = !state.loading) { viewModel.requestExit() }

    if (state.showExitDialog) {
        ExitDialog(onConfirm = { viewModel.dismissExit(); onBack() }, onDismiss = viewModel::dismissExit)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val current = (state.currentIndex + 1).coerceAtMost(state.total.coerceAtLeast(1))
                    Text(stringResource(R.string.exam_progress, current, state.total))
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.requestExit() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.current == null -> Text(
                    text = stringResource(R.string.exam_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                )
                else -> QuestionBody(
                    state = state,
                    onSelect = { viewModel.answer(selectedIndex = it) },
                    onScrambleSubmit = { viewModel.answer(typedText = it) },
                    onCheckOpen = { viewModel.checkOpenAnswer(it) },
                    onSkipOpen = viewModel::skipOpen,
                    onNext = viewModel::next,
                    onSpeak = speak,
                )
            }
        }
    }
}

@Composable
private fun QuestionBody(
    state: ExamUiState,
    onSelect: (Int) -> Unit,
    onScrambleSubmit: (String) -> Unit,
    onCheckOpen: (String) -> Unit,
    onSkipOpen: () -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    val q = state.current ?: return
    val payload = q.payload

    // Speak audio questions automatically as they appear.
    LaunchedEffect(state.currentIndex) {
        if (q.item.type == QuizType.AUDIO_CHOICE) payload.ttsText?.let(onSpeak)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else (state.currentIndex + 1f) / state.total },
            modifier = Modifier.fillMaxWidth(),
        )

        if (q.item.type == QuizType.AUDIO_CHOICE) {
            OutlinedButton(onClick = { payload.ttsText?.let(onSpeak) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.exam_audio_play))
            }
        } else if (payload.prompt.isNotBlank()) {
            Text(payload.prompt, style = MaterialTheme.typography.headlineSmall)
        }

        when (q.item.type) {
            QuizType.LETTER_SCRAMBLE -> ScrambleInput(
                scrambled = payload.prompt,
                revealed = state.revealed,
                onSubmit = onScrambleSubmit,
                key = state.currentIndex,
            )
            QuizType.OPEN_SENTENCE -> OpenSentenceInput(
                term = payload.answerText,
                state = state,
                onCheck = onCheckOpen,
                onSkip = onSkipOpen,
                key = state.currentIndex,
            )
            else -> ChoiceOptions(
                options = payload.options,
                revealed = state.revealed,
                correctIndex = payload.answerIndex,
                userAnswer = q.item.userAnswer,
                onSelect = onSelect,
            )
        }

        if (state.revealed) {
            if (q.item.type == QuizType.OPEN_SENTENCE) {
                OpenFeedback(verdict = state.openVerdict)
            } else {
                Feedback(correct = state.lastCorrect == true, answerText = payload.answerText, explanation = payload.explanation)
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.isLast) stringResource(R.string.exam_finish_short)
                    else stringResource(R.string.exam_next),
                )
            }
        }
    }
}

@Composable
private fun ChoiceOptions(
    options: List<String>,
    revealed: Boolean,
    correctIndex: Int,
    userAnswer: String?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val colors = when {
                revealed && index == correctIndex -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                )
                revealed && option == userAnswer -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                )
                else -> ButtonDefaults.outlinedButtonColors()
            }
            if (revealed) {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(option) }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) {
                    Text(option)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScrambleInput(
    scrambled: String,
    revealed: Boolean,
    onSubmit: (String) -> Unit,
    key: Int,
) {
    // Each letter is a tile identified by its original index so duplicate letters stay distinct.
    val tiles = remember(key) { scrambled.mapIndexed { i, c -> i to c } }
    var assembled by remember(key) { mutableStateOf(listOf<Pair<Int, Char>>()) }
    val used = assembled.map { it.first }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.exam_scramble_hint), style = MaterialTheme.typography.bodyMedium)

        // Assembled row: tap a letter to send it back to the pool.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            assembled.forEach { tile ->
                OutlinedButton(onClick = { if (!revealed) assembled = assembled - tile }, enabled = !revealed) {
                    Text(tile.second.uppercase())
                }
            }
        }
        // Available letters.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tiles.filter { it.first !in used }.forEach { tile ->
                OutlinedButton(onClick = { if (!revealed) assembled = assembled + tile }, enabled = !revealed) {
                    Text(tile.second.uppercase())
                }
            }
        }
        if (!revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSubmit(assembled.map { it.second }.joinToString("")) },
                    enabled = assembled.size == tiles.size,
                ) { Text(stringResource(R.string.exam_check)) }
                TextButton(onClick = { assembled = emptyList() }) {
                    Text(stringResource(R.string.exam_scramble_clear))
                }
            }
        }
    }
}

@Composable
private fun OpenSentenceInput(
    term: String,
    state: ExamUiState,
    onCheck: (String) -> Unit,
    onSkip: () -> Unit,
    key: Int,
) {
    var text by remember(key) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.exam_open_prompt, term),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.revealed && !state.checking,
            minLines = 2,
        )
        if (!state.revealed) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onCheck(text) }, enabled = text.isNotBlank() && !state.checking) {
                    Text(stringResource(R.string.exam_check))
                }
                if (state.checking) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            if (state.checkFailed) {
                Text(
                    stringResource(R.string.exam_check_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onSkip) {
                    Text(stringResource(R.string.exam_skip))
                }
            }
        }
    }
}

@Composable
private fun OpenFeedback(verdict: OpenAnswerVerdict?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (verdict == null) {
            // Skipped after a failed check — neither correct nor wrong.
            Text(
                stringResource(R.string.exam_open_skipped),
                style = MaterialTheme.typography.titleMedium,
            )
            return@Column
        }
        val (labelRes, color) = when (verdict.verdict) {
            OpenAnswerVerdict.Verdict.OK ->
                R.string.exam_open_ok to MaterialTheme.colorScheme.primary
            OpenAnswerVerdict.Verdict.MINOR ->
                R.string.exam_open_minor to MaterialTheme.colorScheme.tertiary
            OpenAnswerVerdict.Verdict.WRONG ->
                R.string.exam_open_wrong to MaterialTheme.colorScheme.error
        }
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        if (!verdict.corrected.isNullOrBlank()) {
            Text(stringResource(R.string.exam_open_corrected, verdict.corrected))
        }
        if (verdict.comment.isNotBlank()) {
            Text(verdict.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, answerText: String, explanation: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (correct) stringResource(R.string.exam_correct)
            else stringResource(R.string.exam_wrong),
            style = MaterialTheme.typography.titleMedium,
            color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!correct) {
            Text(stringResource(R.string.exam_result_correct_answer, answerText))
        }
        if (!explanation.isNullOrBlank()) {
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_exit_title)) },
        text = { Text(stringResource(R.string.exam_exit_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.exam_exit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exam_exit_cancel)) } },
    )
}

/** Remembers a shared [TtsSpeaker] (en-US) tied to the screen and returns a speak(term) callback. */
@Composable
private fun rememberTts(): (String) -> Unit {
    val context = LocalContext.current
    val speaker = remember { TtsSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose { speaker.release() }
    }
    return speaker::speak
}

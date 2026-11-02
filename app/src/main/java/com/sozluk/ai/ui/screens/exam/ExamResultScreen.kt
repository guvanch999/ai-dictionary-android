package com.sozluk.ai.ui.screens.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(
    sessionId: String,
    onOpenWord: (String) -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    viewModel: ExamResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.exam_result_title)) }) },
    ) { innerPadding ->
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(innerPadding).padding(24.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.exam_result_score, state.score, state.total, state.percent),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (state.mistakes.isEmpty()) {
                Text(stringResource(R.string.exam_result_no_mistakes), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.exam_result_mistakes), style = MaterialTheme.typography.titleMedium)
                state.mistakes.forEach { mistake ->
                    MistakeCard(mistake = mistake, onClick = { onOpenWord(mistake.wordId) })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.exam_result_again))
                }
                Button(onClick = onHome, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.exam_result_home))
                }
            }
        }
    }
}

@Composable
private fun MistakeCard(mistake: ExamMistake, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(mistake.term, style = MaterialTheme.typography.titleMedium)
            Text(mistake.question, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text(
                stringResource(R.string.exam_result_your_answer, mistake.yourAnswer),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.exam_result_correct_answer, mistake.correctAnswer),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

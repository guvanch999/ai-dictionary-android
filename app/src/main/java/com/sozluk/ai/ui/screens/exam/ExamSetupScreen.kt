package com.sozluk.ai.ui.screens.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.srs.ExamScope

// "All" question count — capped by the composer to however many items the scope can produce.
private const val ALL_QUESTIONS = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSetupScreen(
    onStartExam: (String) -> Unit,
    onBack: () -> Unit,
    initialScope: ExamScope = ExamScope.ALL,
    viewModel: ExamSetupViewModel = hiltViewModel(),
) {
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.startedFlow.collect { onStartExam(it) }
    }

    var count by remember { mutableIntStateOf(10) }
    var scope by remember { mutableStateOf(initialScope) }
    var includeMastered by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChipSection(
                title = stringResource(R.string.exam_setup_count_label),
                options = listOf(
                    10 to "10",
                    20 to "20",
                    ALL_QUESTIONS to stringResource(R.string.exam_setup_count_all),
                ),
                selected = count,
                onSelect = { count = it },
            )
            ChipSection(
                title = stringResource(R.string.exam_setup_scope_label),
                options = listOf(
                    ExamScope.ALL to stringResource(R.string.exam_setup_scope_all),
                    ExamScope.LAST_WEEK to stringResource(R.string.exam_setup_scope_week),
                    ExamScope.WEAK to stringResource(R.string.exam_setup_scope_weak),
                    ExamScope.DUE to stringResource(R.string.exam_setup_scope_due),
                ),
                selected = scope,
                onSelect = { scope = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.exam_setup_include_mastered), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = includeMastered, onCheckedChange = { includeMastered = it })
            }
            Button(
                onClick = { viewModel.start(count, scope, includeMastered) },
                enabled = !preparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(stringResource(R.string.exam_setup_preparing))
                } else {
                    Text(stringResource(R.string.exam_setup_start))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

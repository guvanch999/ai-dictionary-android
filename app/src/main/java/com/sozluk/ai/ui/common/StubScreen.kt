package com.sozluk.ai.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal placeholder screen: a top bar with a title and a body slot for stub
 * navigation buttons. Real screens replace these in later tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StubScreen(
    title: String,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            body()
        }
    }
}

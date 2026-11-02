package com.sozluk.ai.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozluk.ai.R
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.usage.AiRequestType
import com.sozluk.ai.domain.usage.UsageBucket
import com.sozluk.ai.ui.common.aiErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF pickers: create/open a JSON document, then hand the Uri to the ViewModel.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                SettingsMessage.ExportDone -> context.getString(R.string.settings_export_done)
                is SettingsMessage.ImportDone ->
                    context.getString(R.string.settings_import_report, message.imported, message.skipped)
                SettingsMessage.ImportUnsupported -> context.getString(R.string.settings_import_unsupported)
                SettingsMessage.ImportInvalid -> context.getString(R.string.settings_import_invalid)
                SettingsMessage.Error -> context.getString(R.string.settings_backup_error)
            }
            snackbarHost.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ApiKeySection(state = state, onCheck = viewModel::saveAndCheckKey)
            HorizontalDivider()
            InterfaceLangSection()
            HorizontalDivider()
            NativeLangSection(current = state.nativeLang, onSelect = viewModel::setNativeLang)
            HorizontalDivider()
            ScreenshotWatcherRow(
                enabled = state.screenshotWatcherEnabled,
                onSetEnabled = viewModel::setScreenshotWatcher,
            )
            HorizontalDivider()
            BackupSection(
                onExport = { exportLauncher.launch("sozluk-backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
            )
            HorizontalDivider()
            UsageSection(state = state, onReset = viewModel::resetUsage)
        }
    }
}

@Composable
private fun BackupSection(onExport: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.settings_backup_summary),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onExport) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(onClick = onImport) { Text(stringResource(R.string.settings_import)) }
        }
    }
}

@Composable
private fun UsageSection(state: SettingsUiState, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_usage_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.settings_usage_month), style = MaterialTheme.typography.titleSmall)
        UsageBucketRows(state.usage.month)
        Text(stringResource(R.string.settings_usage_total), style = MaterialTheme.typography.titleSmall)
        UsageBucketRows(state.usage.total)
        Text(
            stringResource(R.string.settings_usage_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onReset) { Text(stringResource(R.string.settings_usage_reset)) }
    }
}

@Composable
private fun UsageBucketRows(bucket: UsageBucket) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.settings_usage_requests, bucket.totalRequests))
        AiRequestType.entries.forEach { type ->
            Text(
                "    ${stringResource(usageTypeLabel(type))}: ${bucket.requests(type)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(stringResource(R.string.settings_usage_tokens, bucket.tokens))
    }
}

private fun usageTypeLabel(type: AiRequestType): Int = when (type) {
    AiRequestType.ARTICLE -> R.string.settings_usage_type_article
    AiRequestType.QUIZ -> R.string.settings_usage_type_quiz
    AiRequestType.VISION -> R.string.settings_usage_type_vision
    AiRequestType.CHECK -> R.string.settings_usage_type_check
}

@Composable
private fun ApiKeySection(state: SettingsUiState, onCheck: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_api_key_label),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(
                        if (visible) stringResource(R.string.settings_api_key_hide)
                        else stringResource(R.string.settings_api_key_show),
                    )
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onCheck(key) },
                enabled = key.isNotBlank() && state.checkState != CheckState.Checking,
            ) {
                Text(stringResource(R.string.settings_check_key))
            }
            when (val cs = state.checkState) {
                is CheckState.Checking -> CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                is CheckState.Ok -> Text(stringResource(R.string.settings_check_ok))
                is CheckState.Fail -> Text(aiErrorMessage(cs.error))
                is CheckState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InterfaceLangSection() {
    // AppCompat persists the choice (autoStoreLocales) and recreates the activity on change, so the
    // current selection can be read directly from AppCompatDelegate each recomposition (spec §8.1).
    val current = AppCompatDelegate.getApplicationLocales()
    val currentLang = if (current.isEmpty) null else current[0]?.language

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_interface_lang),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LangChip(stringResource(R.string.settings_interface_lang_system), currentLang == null) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            LangChip(stringResource(R.string.lang_en), currentLang == "en") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
            LangChip(stringResource(R.string.lang_ru), currentLang == "ru") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
            }
            LangChip(stringResource(R.string.lang_tk), currentLang == "tk") {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("tk"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeLangSection(current: NativeLang, onSelect: (NativeLang) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_native_lang_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = current == NativeLang.RU,
                onClick = { onSelect(NativeLang.RU) },
                label = { Text(stringResource(R.string.lang_ru)) },
            )
            FilterChip(
                selected = current == NativeLang.TK,
                onClick = { onSelect(NativeLang.TK) },
                label = { Text(stringResource(R.string.lang_tk)) },
            )
        }
    }
}


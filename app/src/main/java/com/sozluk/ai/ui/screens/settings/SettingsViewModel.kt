package com.sozluk.ai.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.data.backup.BackupManager
import com.sozluk.ai.data.backup.ImportOutcome
import com.sozluk.ai.data.prefs.ApiKeyStore
import com.sozluk.ai.data.screenshot.ScreenshotWatcherService
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.usage.UsageCounter
import com.sozluk.ai.domain.usage.UsageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the live API-key check (spec F-7). */
sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object Ok : CheckState
    data class Fail(val error: AiError) : CheckState
}

/** One-shot user feedback for export/import actions (shown as a snackbar). */
sealed interface SettingsMessage {
    data object ExportDone : SettingsMessage
    data class ImportDone(val imported: Int, val skipped: Int) : SettingsMessage
    data object ImportUnsupported : SettingsMessage
    data object ImportInvalid : SettingsMessage
    data object Error : SettingsMessage
}

data class SettingsUiState(
    val hasKey: Boolean = false,
    val checkState: CheckState = CheckState.Idle,
    val nativeLang: NativeLang = NativeLang.RU,
    val screenshotWatcherEnabled: Boolean = false,
    val usage: UsageStats = UsageStats(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val aiClient: AiClient,
    private val settingsRepository: SettingsRepository,
    private val usageCounter: UsageCounter,
    private val backupManager: BackupManager,
    @param:Named("io") private val io: CoroutineDispatcher,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val hasKey = MutableStateFlow(false)
    private val checkState = MutableStateFlow<CheckState>(CheckState.Idle)

    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)
    /** One-shot messages for the screen to surface (export/import outcomes). */
    val messages: Flow<SettingsMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            hasKey,
            checkState,
            settingsRepository.nativeLang,
            settingsRepository.screenshotWatcherEnabled,
            usageCounter.usage,
        ) { hk, cs, lang, watcher, usage ->
            SettingsUiState(
                hasKey = hk,
                checkState = cs,
                nativeLang = lang,
                screenshotWatcherEnabled = watcher,
                usage = usage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { hasKey.value = withContext(io) { apiKeyStore.hasKey() } }
    }

    /** Saves the key and fires a live mini-request to verify it (spec F-7). */
    fun saveAndCheckKey(key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            withContext(io) { apiKeyStore.setKey(trimmed) }
            hasKey.value = trimmed.isNotEmpty()
            checkState.value = CheckState.Checking
            checkState.value = aiClient.explainWord("test", null).fold(
                onSuccess = { CheckState.Ok },
                onFailure = { CheckState.Fail(it as? AiError ?: AiError.Unknown(it.message ?: "")) },
            )
        }
    }

    fun setNativeLang(lang: NativeLang) {
        viewModelScope.launch { settingsRepository.setNativeLang(lang) }
    }

    /**
     * Commits the screenshot-watcher toggle (task 10). Only call with [enabled] = true once the
     * onboarding has granted every permission; [enabled] = false persists off and tears the service
     * down instantly. Persist first, then start/stop so the service's flag re-check stays consistent.
     */
    fun setScreenshotWatcher(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScreenshotWatcherEnabled(enabled)
            if (enabled) ScreenshotWatcherService.start(appContext)
            else ScreenshotWatcherService.stop(appContext)
        }
    }

    /** Writes the whole dictionary as JSON to the SAF-picked [uri] (spec F-7). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(io) {
                runCatching {
                    val json = backupManager.exportAll()
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: error("no output stream")
                }.isSuccess
            }
            messageChannel.send(if (ok) SettingsMessage.ExportDone else SettingsMessage.Error)
        }
    }

    /** Reads and merge-imports a backup file from the SAF-picked [uri] (spec F-7). */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val outcome = withContext(io) {
                runCatching {
                    val text = appContext.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: error("no input stream")
                    backupManager.import(text)
                }
            }
            val message = outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is ImportOutcome.Success ->
                            SettingsMessage.ImportDone(result.report.imported, result.report.skipped)
                        ImportOutcome.UnsupportedVersion -> SettingsMessage.ImportUnsupported
                        ImportOutcome.Invalid -> SettingsMessage.ImportInvalid
                    }
                },
                onFailure = { SettingsMessage.Error },
            )
            messageChannel.send(message)
        }
    }

    fun resetUsage() {
        viewModelScope.launch { usageCounter.reset() }
    }
}

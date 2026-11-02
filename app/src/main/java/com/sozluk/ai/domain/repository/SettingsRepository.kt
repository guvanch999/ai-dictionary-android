package com.sozluk.ai.domain.repository

import com.sozluk.ai.domain.model.NativeLang
import kotlinx.coroutines.flow.Flow

/** App preferences stored in DataStore (spec F-7). The API key lives in EncryptedSharedPreferences. */
interface SettingsRepository {
    /** Native language for translation order; defaults to [NativeLang.RU]. */
    val nativeLang: Flow<NativeLang>

    suspend fun setNativeLang(lang: NativeLang)

    /** Whether the screenshot watcher is opt-in enabled (spec F-6, task 10); defaults to false. */
    val screenshotWatcherEnabled: Flow<Boolean>

    suspend fun setScreenshotWatcherEnabled(enabled: Boolean)
}

package com.sozluk.ai.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val nativeLang: Flow<NativeLang> = dataStore.data.map { prefs ->
        when (prefs[KEY_NATIVE_LANG]) {
            NativeLang.TK.name -> NativeLang.TK
            else -> NativeLang.RU // default (spec §8.2)
        }
    }

    override suspend fun setNativeLang(lang: NativeLang) {
        dataStore.edit { it[KEY_NATIVE_LANG] = lang.name }
    }

    override val screenshotWatcherEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SCREENSHOT_WATCHER] ?: false // opt-in, off by default (task 10)
    }

    override suspend fun setScreenshotWatcherEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SCREENSHOT_WATCHER] = enabled }
    }

    private companion object {
        val KEY_NATIVE_LANG = stringPreferencesKey("native_lang")
        val KEY_SCREENSHOT_WATCHER = booleanPreferencesKey("screenshot_watcher_enabled")
    }
}

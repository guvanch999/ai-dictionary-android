package com.sozluk.ai.data.prefs

import androidx.appcompat.app.AppCompatDelegate
import com.sozluk.ai.domain.i18n.InterfaceLocaleProvider
import com.sozluk.ai.domain.i18n.interfaceLangFromTag
import java.util.Locale
import javax.inject.Inject

/**
 * Reads the per-app locale chosen via [AppCompatDelegate.setApplicationLocales]; when the user
 * follows the system default (empty list), falls back to the system locale. Never throws — an
 * unsupported locale resolves to English (spec §8.1).
 */
class AppCompatInterfaceLocaleProvider @Inject constructor() : InterfaceLocaleProvider {
    override fun current(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!appLocales.isEmpty) appLocales[0]?.language else Locale.getDefault().language
        return interfaceLangFromTag(tag)
    }
}

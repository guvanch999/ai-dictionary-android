package com.sozluk.ai.domain.i18n

/**
 * Current interface (UI) language as a plain BCP-47 base tag: "en", "ru" or "tk".
 *
 * This is deliberately independent of the native translation language ([NativeLang]
 * [com.sozluk.ai.domain.model.NativeLang], which only orders the card's translations, spec §8.2).
 * The UI language is what drives AI question/explanation wording in quiz generation (spec §6.2).
 */
fun interface InterfaceLocaleProvider {
    fun current(): String
}

/** Supported interface languages; the AI prompt receives one of these. */
val SUPPORTED_INTERFACE_LANGS = listOf("en", "ru", "tk")

/**
 * Maps a locale or language tag (e.g. "ru", "tk-TM", "en_US", null) to a supported interface
 * language, falling back to English. Pure function — unit-tested.
 */
fun interfaceLangFromTag(tag: String?): String {
    val lang = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
    return if (lang in SUPPORTED_INTERFACE_LANGS) lang!! else "en"
}

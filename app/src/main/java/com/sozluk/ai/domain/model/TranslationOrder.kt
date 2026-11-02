package com.sozluk.ai.domain.model

/** Translations ordered by the user's native language: (first shown, second shown) — spec §8.2. */
fun WordEntry.translationsFor(lang: NativeLang): Pair<String, String> =
    when (lang) {
        NativeLang.RU -> translationRu to translationTk
        NativeLang.TK -> translationTk to translationRu
    }

/** The example's translation in the native language (spec §8.2). */
fun Example.translationFor(lang: NativeLang): String =
    when (lang) {
        NativeLang.RU -> ru
        NativeLang.TK -> tk
    }

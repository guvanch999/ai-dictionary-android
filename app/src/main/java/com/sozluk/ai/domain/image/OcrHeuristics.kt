package com.sozluk.ai.domain.image

import com.sozluk.ai.domain.text.TextSelection

/**
 * Deciding whether ML Kit's output is usable or the crop should fall back to the vision model
 * (spec F-6, step 2). Pure and unit tested.
 */
object OcrHeuristics {

    private const val MIN_WORD_LETTERS = 2

    /**
     * True when [text] has no real word — i.e. no token with at least [MIN_WORD_LETTERS] letters.
     * Empty output, whitespace, punctuation ("...") or stray single characters count as garbage
     * and trigger the Claude vision fallback.
     */
    fun isGarbage(text: String): Boolean =
        TextSelection.tokenize(text).none { token ->
            token.count(Char::isLetter) >= MIN_WORD_LETTERS
        }
}

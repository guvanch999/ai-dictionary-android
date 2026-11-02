package com.sozluk.ai.domain.text

/**
 * Splitting of a text selection into words for the PROCESS_TEXT lookup flow (spec F-5).
 * A "word" keeps inner apostrophes/hyphens (don't, well-known) but drops surrounding punctuation.
 */
object TextSelection {

    // Any run of characters that is not a letter, digit, apostrophe or hyphen separates words.
    private val DELIMITERS = Regex("[^\\p{L}\\p{Nd}'-]+")

    /** Words in [text], preserving order; punctuation-only fragments are dropped. */
    fun tokenize(text: String): List<String> =
        text.split(DELIMITERS)
            .map { it.trim('\'', '-') }
            .filter { token -> token.any(Char::isLetterOrDigit) }

    /** Number of words in [text] (0 for blank/punctuation-only selections). */
    fun wordCount(text: String): Int = tokenize(text).size
}

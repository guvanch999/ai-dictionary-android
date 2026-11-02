package com.sozluk.ai.data.ai

/**
 * Extract the JSON object from a raw model reply: take the substring from the first `{` to the
 * last `}` (spec §6.1). This transparently drops ```json code fences and any text around the
 * object. Returns null when no object is present.
 */
internal fun extractJson(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start != -1 && end > start) raw.substring(start, end + 1) else null
}

/**
 * Extract a JSON array from a raw model reply: take the substring from the first `[` to the last
 * `]` (spec §6.2, quiz batch). Like [extractJson] this transparently drops ```json code fences and
 * any surrounding text. Returns null when no array is present.
 */
internal fun extractJsonArray(raw: String): String? {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    return if (start != -1 && end > start) raw.substring(start, end + 1) else null
}

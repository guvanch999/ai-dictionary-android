package com.sozluk.ai.domain.repository

/**
 * Persistence for screenshot crops (spec F-6, step 5). Domain-facing so use cases stay off the
 * data layer; the implementation writes to internal storage.
 */
interface CropStore {
    /** Stores [jpeg] for [wordId] and returns the absolute path to save in `imagePath`. */
    fun save(wordId: String, jpeg: ByteArray): String

    /** Removes the crop file at [path] (best-effort), called when a word is deleted. */
    fun delete(path: String)
}

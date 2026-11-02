package com.sozluk.ai.data.image

import android.content.Context
import com.sozluk.ai.domain.repository.CropStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Stores screenshot crops in internal storage under `filesDir/crops/{wordId}.jpg` (spec F-6,
 * step 5). The absolute path is what gets persisted in [com.sozluk.ai.domain.model.WordEntry.imagePath];
 * the file is removed when its word is deleted.
 */
class CropStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CropStore {

    private val cropsDir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Writes [jpeg] for [wordId] and returns its absolute path (used as `imagePath`). */
    override fun save(wordId: String, jpeg: ByteArray): String {
        val file = File(cropsDir, "$wordId.jpg")
        file.writeBytes(jpeg)
        return file.absolutePath
    }

    /** Deletes the crop file at [path] if it exists (best-effort cleanup on word deletion). */
    override fun delete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private companion object {
        const val DIR_NAME = "crops"
    }
}

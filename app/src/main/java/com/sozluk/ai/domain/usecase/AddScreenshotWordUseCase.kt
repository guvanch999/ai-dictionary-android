package com.sozluk.ai.domain.usecase

import com.sozluk.ai.domain.ai.WordArticle
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.toWordEntry
import com.sozluk.ai.domain.repository.CropStore
import com.sozluk.ai.domain.repository.WordRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Persists a word that came from a screenshot (spec F-6, step 5): stores the crop JPEG in internal
 * storage, tags the entry SCREENSHOT with its `imagePath`, and upserts it. The [article] is already
 * resolved (either from the ML Kit → text path or straight from the vision model).
 */
class AddScreenshotWordUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val cropStore: CropStore,
) {
    suspend operator fun invoke(
        article: WordArticle,
        term: String,
        contextSentence: String?,
        cropJpeg: ByteArray,
    ): WordEntry {
        // Id chosen up front so the crop file name and the entry match.
        val id = UUID.randomUUID().toString()
        val imagePath = cropStore.save(id, cropJpeg)
        val entry = article.toWordEntry(
            term = term,
            contextSentence = contextSentence,
            sourceType = SourceType.SCREENSHOT,
            id = id,
            imagePath = imagePath,
        )
        wordRepository.upsert(entry)
        return entry
    }
}

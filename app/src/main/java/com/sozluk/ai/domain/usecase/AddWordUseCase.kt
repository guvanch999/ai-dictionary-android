package com.sozluk.ai.domain.usecase

import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.model.toWordEntry
import com.sozluk.ai.domain.repository.WordRepository
import javax.inject.Inject

/**
 * Explains a word via AI and persists it, tagged with [SourceType]. Shared by the Home add flow
 * (MANUAL) and the PROCESS_TEXT lookup card so the AI request logic is not duplicated (spec F-5).
 * Dedup and error presentation stay the caller's responsibility.
 */
class AddWordUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val aiClient: AiClient,
) {
    suspend operator fun invoke(
        term: String,
        contextSentence: String?,
        sourceType: SourceType,
    ): Result<WordEntry> =
        aiClient.explainWord(term, contextSentence).map { article ->
            val entry = article.toWordEntry(term, contextSentence, sourceType)
            wordRepository.upsert(entry)
            entry
        }
}

package com.sozluk.ai.domain.usecase

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sozluk.ai.domain.image.OcrHeuristics
import com.sozluk.ai.domain.text.TextSelection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.resume

/** Outcome of the on-device OCR step of the cascade (spec F-6, step 3). */
sealed interface OcrResult {
    /** ML Kit read usable text: [terms] to choose from, [context] is the full crop text. */
    data class Words(val terms: List<String>, val context: String) : OcrResult

    /** ML Kit returned nothing usable — fall back to the vision model. */
    data object NeedsVision : OcrResult
}

/**
 * Step 1 of the OCR cascade: on-device Latin text recognition on the crop (spec F-6). If the text
 * is garbage ([OcrHeuristics.isGarbage]) or recognition fails, returns [OcrResult.NeedsVision] so
 * the caller can fall back to the Claude vision path.
 */
class RecognizeWordFromImage @Inject constructor(
    @param:Named("io") private val ioDispatcher: CoroutineDispatcher,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend operator fun invoke(crop: Bitmap): OcrResult = withContext(ioDispatcher) {
        val text = recognize(crop) ?: return@withContext OcrResult.NeedsVision
        if (OcrHeuristics.isGarbage(text)) {
            OcrResult.NeedsVision
        } else {
            OcrResult.Words(terms = TextSelection.tokenize(text), context = text.trim())
        }
    }

    /** Bridges ML Kit's Task API to a suspend function; null on any recognition failure. */
    private suspend fun recognize(crop: Bitmap): String? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(crop, 0)
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { cont.resume(null) }
        }
}

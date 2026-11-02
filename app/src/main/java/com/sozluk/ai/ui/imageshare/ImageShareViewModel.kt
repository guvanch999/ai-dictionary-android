package com.sozluk.ai.ui.imageshare

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozluk.ai.R
import com.sozluk.ai.data.image.ImageDecoder
import com.sozluk.ai.data.image.toJpeg
import com.sozluk.ai.domain.ai.AiClient
import com.sozluk.ai.domain.ai.AiError
import com.sozluk.ai.domain.image.IntBox
import com.sozluk.ai.domain.model.NativeLang
import com.sozluk.ai.domain.model.WordEntry
import com.sozluk.ai.domain.repository.SettingsRepository
import com.sozluk.ai.domain.repository.WordRepository
import com.sozluk.ai.domain.usecase.AddScreenshotWordUseCase
import com.sozluk.ai.domain.usecase.OcrResult
import com.sozluk.ai.domain.usecase.RecognizeWordFromImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state of the screenshot share/annotate flow (spec F-6). */
sealed interface ImageShareUiState {
    /** Decoding the shared image. */
    data object Loading : ImageShareUiState

    /** Image is ready; user draws over the word to crop. */
    data class Annotating(val bitmap: Bitmap) : ImageShareUiState

    /** Running the OCR cascade / AI call on the crop. */
    data object Processing : ImageShareUiState

    /** ML Kit found several words: pick one; the whole crop text is the context. */
    data class WordPicker(val words: List<String>, val context: String) : ImageShareUiState

    data class Saved(val entry: WordEntry, val nativeLang: NativeLang) : ImageShareUiState

    /** Recoverable AI failure: retry the crop or go back to the editor. */
    data class Error(val error: AiError) : ImageShareUiState

    /** Unusable input (bad Uri / decode failed): only option is to close. */
    data class Fatal(@param:StringRes val messageRes: Int) : ImageShareUiState

    data object NoApiKey : ImageShareUiState
}

@HiltViewModel
class ImageShareViewModel @Inject constructor(
    private val imageDecoder: ImageDecoder,
    private val recognizeWord: RecognizeWordFromImage,
    private val addScreenshotWord: AddScreenshotWordUseCase,
    private val aiClient: AiClient,
    private val wordRepository: WordRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImageShareUiState>(ImageShareUiState.Loading)
    val uiState: StateFlow<ImageShareUiState> = _state.asStateFlow()

    private var nativeLang: NativeLang = NativeLang.RU
    private var sourceBitmap: Bitmap? = null
    private var lastCrop: Bitmap? = null
    private var lastCropJpeg: ByteArray? = null
    private var lastContext: String? = null
    private var loaded = false

    init {
        viewModelScope.launch { settingsRepository.nativeLang.collect { nativeLang = it } }
    }

    /** Called once with the shared image Uri; decodes (downsampled) and enters the editor. */
    fun load(uri: Uri) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val bitmap = imageDecoder.decodeDownsampled(uri)
            if (bitmap == null) {
                _state.value = ImageShareUiState.Fatal(R.string.error_image_unsupported)
            } else {
                sourceBitmap = bitmap
                _state.value = ImageShareUiState.Annotating(bitmap)
            }
        }
    }

    /** The editor confirmed a crop rectangle (in bitmap pixel coordinates). */
    fun onCropConfirmed(box: IntBox) {
        val source = sourceBitmap ?: return
        val crop = runCatching {
            Bitmap.createBitmap(source, box.left, box.top, box.width, box.height)
        }.getOrNull()
        if (crop == null) {
            _state.value = ImageShareUiState.Fatal(R.string.error_image_unsupported)
            return
        }
        lastCrop = crop
        lastCropJpeg = crop.toJpeg()
        runCascade(crop)
    }

    /** A word tapped in the picker becomes the term; the crop text is the context. */
    fun onWordSelected(word: String) {
        _state.value = ImageShareUiState.Processing
        resolveViaText(word, lastContext)
    }

    /** Retry re-runs the whole cascade on the last crop. */
    fun onRetry() {
        val crop = lastCrop ?: return
        runCascade(crop)
    }

    /** Discard the current attempt and return to drawing. */
    fun onBackToEditor() {
        val source = sourceBitmap ?: return
        _state.value = ImageShareUiState.Annotating(source)
    }

    private fun runCascade(crop: Bitmap) {
        _state.value = ImageShareUiState.Processing
        viewModelScope.launch {
            when (val ocr = recognizeWord(crop)) {
                is OcrResult.Words -> {
                    lastContext = ocr.context.takeIf { ocr.terms.size > 1 }
                    when (ocr.terms.size) {
                        0 -> resolveViaVision()
                        1 -> resolveViaText(ocr.terms.first(), contextSentence = null)
                        else -> _state.value = ImageShareUiState.WordPicker(ocr.terms, ocr.context)
                    }
                }
                OcrResult.NeedsVision -> resolveViaVision()
            }
        }
    }

    /** ML Kit path: explain the recognized term as text, then persist with the crop thumbnail. */
    private fun resolveViaText(term: String, contextSentence: String?) {
        val jpeg = lastCropJpeg ?: return
        _state.value = ImageShareUiState.Processing
        viewModelScope.launch {
            val existing = wordRepository.findByNormalizedTerm(term.trim())
            if (existing.isNotEmpty()) {
                _state.value = ImageShareUiState.Saved(existing.first(), nativeLang)
                return@launch
            }
            aiClient.explainWord(term, contextSentence)
                .onSuccess { article ->
                    val entry = addScreenshotWord(article, term, contextSentence, jpeg)
                    _state.value = ImageShareUiState.Saved(entry, nativeLang)
                }
                .onFailure { setError(it) }
        }
    }

    /** Vision fallback: Claude reads the crop and returns the article directly (spec F-6, step 4). */
    private fun resolveViaVision() {
        val jpeg = lastCropJpeg ?: return
        _state.value = ImageShareUiState.Processing
        viewModelScope.launch {
            aiClient.explainImage(jpeg, JPEG_MEDIA_TYPE)
                .onSuccess { article ->
                    val entry = addScreenshotWord(article, article.term, null, jpeg)
                    _state.value = ImageShareUiState.Saved(entry, nativeLang)
                }
                .onFailure { setError(it) }
        }
    }

    private fun setError(error: Throwable) {
        _state.value = when (error) {
            is AiError.NoApiKey -> ImageShareUiState.NoApiKey
            is AiError -> ImageShareUiState.Error(error)
            else -> ImageShareUiState.Error(AiError.Unknown(error.message ?: ""))
        }
    }

    private companion object {
        const val JPEG_MEDIA_TYPE = "image/jpeg"
    }
}

package com.sozluk.ai.ui.common

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pronounces English text via Android TTS (en-US). Shared by the word card (task 09) and the exam
 * audio questions (spec §7.2). The engine is created eagerly per owner (lazily relative to app
 * start) and must be freed with [release]. [available] stays false when no TTS engine is installed
 * or the en-US voice is missing, so callers can hide the play button instead of failing silently.
 */
class TtsSpeaker(context: Context) {

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val result = if (status == TextToSpeech.SUCCESS) engine?.setLanguage(Locale.US) else null
            _available.value = result != null &&
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}

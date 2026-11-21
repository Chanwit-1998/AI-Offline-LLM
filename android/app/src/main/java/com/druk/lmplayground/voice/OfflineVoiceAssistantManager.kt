package com.druk.ai_offline_llm.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class OfflineVoiceAssistantManager(
    context: Context,
    private val onSpeechFinishedForLoop: () -> Unit,
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _voiceReplyEnabled = MutableStateFlow(false)
    val voiceReplyEnabled: StateFlow<Boolean> = _voiceReplyEnabled.asStateFlow()

    private val _continuousLoopEnabled = MutableStateFlow(false)
    val continuousLoopEnabled: StateFlow<Boolean> = _continuousLoopEnabled.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                val thResult = tts?.setLanguage(Locale("th", "TH"))
                if (thResult == TextToSpeech.LANG_MISSING_DATA || thResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(1.05f)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                if (_continuousLoopEnabled.value) onSpeechFinishedForLoop()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _isSpeaking.value = false }
        })
    }

    fun toggleVoiceMode() {
        when {
            !_voiceReplyEnabled.value -> { _voiceReplyEnabled.value = true; _continuousLoopEnabled.value = false }
            _voiceReplyEnabled.value && !_continuousLoopEnabled.value -> { _continuousLoopEnabled.value = true }
            else -> { _voiceReplyEnabled.value = false; _continuousLoopEnabled.value = false; stopSpeaking() }
        }
    }

    fun speakResponse(rawMarkdown: String) {
        if (!_voiceReplyEnabled.value || !isReady) return
        val clean = rawMarkdown
            .replace(Regex("```[\s\S]*?```"), " โค้ดตัวอย่าง ")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\*\*|__|~~|#+|>"), "")
            .replace(Regex("\[([^\]]+)]\([^)]+\)"), "$1")
            .replace(Regex("\s+"), " ")
            .trim()
            .take(1200)
        if (clean.isBlank()) return

        val hasThai = clean.any { it in '฀'..'๿' }
        val targetLocale = if (hasThai) Locale("th", "TH") else Locale.US
        if ((tts?.isLanguageAvailable(targetLocale) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = targetLocale
        }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        _isSpeaking.value = false
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stopSpeaking()
        runCatching { tts?.shutdown() }
        tts = null
    }
}

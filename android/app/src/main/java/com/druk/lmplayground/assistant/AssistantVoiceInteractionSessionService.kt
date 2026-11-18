package com.druk.ai_offline_llm.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Minimal session service stub. Returns an empty [VoiceInteractionSession] so
 * the framework has a valid session class to instantiate. The session itself
 * does nothing (no UI, no request handling).
 */
class AssistantVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AssistantVoiceInteractionSession(this)
    }
}

class AssistantVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    // All lifecycle/request handlers use the default (no-op) implementations.
}

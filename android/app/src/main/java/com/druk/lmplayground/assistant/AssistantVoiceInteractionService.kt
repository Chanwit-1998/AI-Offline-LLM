package com.druk.lmplayground.assistant

import android.service.voice.VoiceInteractionService

/**
 * Minimal VoiceInteractionService stub.
 *
 * Registering this service (with the BIND_VOICE_INTERACTION permission and the
 * android.service.voice.VoiceInteractionService intent filter) is what makes the
 * app appear in Settings > Apps > Default apps > Digital assistant app.
 *
 * No actual voice/assistant functionality is implemented — onReady() is a no-op
 * and the session service below returns an empty session. This only enables
 * *selection* as the default assistant; invoking it does nothing useful.
 */
class AssistantVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // No-op: no hotword detection, no always-on listening.
    }
}

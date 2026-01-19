package xyz.block.gosling.features.assistant

import android.content.Intent
import android.service.voice.VoiceInteractionService

class AssistantService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()

        val intent = Intent(this, AssistantSessionService::class.java)
        startService(intent)
    }
}

package xyz.block.gosling.features.assistant

import android.content.Intent
import android.service.voice.VoiceInteractionService
import xyz.block.gosling.SessionService

class AssistantService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()

        val intent = Intent(this, SessionService::class.java)
        startService(intent)
    }
}

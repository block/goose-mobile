package xyz.block.gosling.features.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.shared.services.VoiceRecognitionService

private const val TAG = "AssistantSessionService"

class AssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        return Session(this)
    }

    private class Session(context: Context) : VoiceInteractionSession(context) {
        private var voiceRecognitionManager: VoiceRecognitionService? = null

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)

            val intent = Intent(context, AssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            startSpeechRecognition()
        }

        private fun startSpeechRecognition() {
            voiceRecognitionManager = VoiceRecognitionService(context)

            voiceRecognitionManager?.startVoiceRecognition(
                object : VoiceRecognitionService.VoiceRecognitionCallback {
                    override fun onVoiceCommandReceived(command: String) {
                        if (command.isNotEmpty()) {
                            Log.d(TAG, "Command: $command")
                            // Process the command with the Agent
                            processAgentCommand(command)
                        }
                        hide()
                    }

                    override fun onPartialResult(partialCommand: String) {
                        Log.d(TAG, "Partial command: $partialCommand")
                    }

                    override fun onError(errorMessage: String) {
                        Log.d(TAG, "Error occurred: $errorMessage")
                        hide()
                    }

                    override fun onListening() {
                        Log.d(TAG, "Ready for speech.")
                    }

                    override fun onSpeechEnd() {
                        Log.d(TAG, "End of speech.")
                        hide()
                    }
                },
            )

            Log.d(TAG, "Started listening for speech.")
        }

        private fun processAgentCommand(command: String) {
            val agentServiceManager = AgentServiceManager(context)

            agentServiceManager.bindAndStartAgent { agent ->
                agent.setStatusListener { status ->
                    when (status) {
                        is AgentStatus.Processing -> {
                            if (status.message.isEmpty() || status.message == "null") return@setStatusListener
                            Log.d(TAG, "Processing: ${status.message}")
                        }

                        is AgentStatus.Success -> {
                            Log.d(TAG, "Success: ${status.message}")
                        }

                        is AgentStatus.Error -> {
                            Log.d(TAG, "Error: ${status.message}")
                        }
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        agent.processCommand(
                            userInput = command,
                            context = context,
                            triggerType = Agent.TriggerType.ASSISTANT
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing command: ${e.message}")
                    }
                }
            }
        }

        override fun onHide() {
            super.onHide()
            voiceRecognitionManager?.stopVoiceRecognition()
            voiceRecognitionManager = null
        }
    }
}

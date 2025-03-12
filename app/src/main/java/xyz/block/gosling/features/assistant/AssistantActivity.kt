package xyz.block.gosling.features.assistant

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.shared.services.VoiceRecognitionService
import xyz.block.gosling.shared.theme.GoslingTheme

class AssistantActivity : ComponentActivity() {
    private var isVoiceInteraction = false
    private lateinit var voiceRecognitionManager: VoiceRecognitionService
    private var currentAgentManager: AgentServiceManager? = null
    private var currentJob: Job? = null
    private val currentTranscription = mutableStateOf("Listening...")
    private var isProcessingCommand = false
    private var commandProcessingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the main activity is already running, finish this activity
        if (GoslingApplication.isMainActivityRunning) {
            finish()
            return
        }

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        window.attributes.apply {
            dimAmount = 0f
            format = android.graphics.PixelFormat.TRANSLUCENT
        }

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)

        isVoiceInteraction = intent?.action == Intent.ACTION_ASSIST
        voiceRecognitionManager = VoiceRecognitionService(this)

        setContent {
            GoslingTheme {
                AssistantUI(
                    isVoiceActive = isVoiceInteraction,
                    currentText = currentTranscription.value,
                    onClose = { finish() }
                )
            }
        }

        // Start voice recognition if this is a voice interaction
        if (isVoiceInteraction) {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        if (!voiceRecognitionManager.hasRecordAudioPermission()) {
            voiceRecognitionManager.requestRecordAudioPermission(this)
            return
        }

        voiceRecognitionManager.startVoiceRecognition(
            object : VoiceRecognitionService.VoiceRecognitionCallback {
                override fun onVoiceCommandReceived(command: String) {
                    currentTranscription.value = command
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000) // Delay to ensure the user has time to see the command
                        finish()
                        processAgentCommand(command)
                    }
                }

                override fun onPartialResult(partialCommand: String) {
                    currentTranscription.value = partialCommand.ifEmpty { "Listening..." }
                }

                override fun onError(errorMessage: String) {
                    currentTranscription.value = errorMessage
                    Toast.makeText(this@AssistantActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onSpeechEnd() {
                    // Do nothing - we'll handle dismissal in onVoiceCommandReceived
                }

                override fun onListening() {
                    currentTranscription.value = "Listening..."
                }
            }
        )
    }

    private fun processAgentCommand(command: String) {
        // Cancel any existing job
        currentJob?.cancel()

        // Unbind any existing service
        currentAgentManager?.unbindAgent()

        // Create new agent manager
        val agentServiceManager = AgentServiceManager(this)
        currentAgentManager = agentServiceManager

        agentServiceManager.bindAndStartAgent { agent ->
            agent.setStatusListener { status ->
                when (status) {
                    is AgentStatus.Processing -> {
                        if (status.message.isEmpty() || status.message == "null") return@setStatusListener
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                status.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    is AgentStatus.Success -> {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                status.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    }

                    is AgentStatus.Error -> {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Error: ${status.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    }
                }
            }

            // Launch in a coroutine scope and store the job
            currentJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    agent.processCommand(
                        userInput = command,
                        context = this@AssistantActivity,
                        isNotificationReply = false
                    )
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AssistantActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing jobs
        currentJob?.cancel()
        commandProcessingJob?.cancel()
        // Unbind the service if it exists
        currentAgentManager?.unbindAgent()
        // Stop voice recognition
        voiceRecognitionManager.stopVoiceRecognition()
    }
}



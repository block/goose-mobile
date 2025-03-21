package xyz.block.gosling.features.assistant

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.block.gosling.features.accessibility.GoslingAccessibilityService
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.features.agent.ToolHandler.buildCompactHierarchy
import xyz.block.gosling.features.overlay.OverlayService
import xyz.block.gosling.shared.services.VoiceRecognitionService
import xyz.block.gosling.shared.theme.GoslingTheme

class AssistantActivity : ComponentActivity() {
    private var isVoiceInteraction = false
    private lateinit var voiceRecognitionManager: VoiceRecognitionService
    private var currentAgentManager: AgentServiceManager? = null
    private var currentJob: Job? = null
    private val currentTranscription = mutableStateOf("Listening...")
    private var foregroundAppPackage: String? = null
    private var screenHierarchy: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_ASSIST) {
            foregroundAppPackage = intent.getStringExtra(Intent.EXTRA_ASSIST_PACKAGE)

            val accessibilityService = GoslingAccessibilityService.getInstance()
            val activeWindow = accessibilityService?.rootInActiveWindow
            if (activeWindow != null) {
                screenHierarchy = buildCompactHierarchy(activeWindow)
            }
        }

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        window.attributes.apply {
            dimAmount = 0f
            format = PixelFormat.TRANSLUCENT
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
                        delay(500) // Delay to ensure the user has time to see the command
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
        currentJob?.cancel()
        currentAgentManager?.unbindAgent()

        val agentServiceManager = AgentServiceManager(this)
        currentAgentManager = agentServiceManager

        OverlayService.getInstance()?.setIsPerformingAction(true)

        val statusToast = Toast.makeText(applicationContext, "", Toast.LENGTH_SHORT)

        agentServiceManager.bindAndStartAgent { agent ->
            agent.setStatusListener { status ->
                when (status) {
                    is AgentStatus.Processing -> {
                        if (status.message.isEmpty() || status.message == "null") return@setStatusListener
                        runOnUiThread {
                            statusToast.setText(status.message)
                            statusToast.show()
                        }
                    }

                    is AgentStatus.Success -> {
                        runOnUiThread {
                            statusToast.setText(status.message)
                            statusToast.show()
                            OverlayService.getInstance()?.setIsPerformingAction(false)
                        }
                        finish()
                    }

                    is AgentStatus.Error -> {
                        runOnUiThread {
                            statusToast.setText(status.message)
                            statusToast.show()
                            OverlayService.getInstance()?.setIsPerformingAction(false)
                        }
                        finish()
                    }
                }
            }

            // Launch in a coroutine scope and store the job
            currentJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val commandWithContext =
                        if (foregroundAppPackage != null && screenHierarchy != null) {
                            "The app the user had open when the assistant was called is " +
                                    "$foregroundAppPackage. Here is the screen hierarchy:\n" +
                                    "$screenHierarchy\n\n" +
                                    "Operate on the current app if that seems relevant. " +
                                    "If not you can start a different app. " +
                                    "Here are the instructions:\n${command}"
                        } else {
                            command
                        }
                    agent.processCommand(
                        userInput = commandWithContext,
                        context = this@AssistantActivity,
                        triggerType = Agent.TriggerType.ASSISTANT
                    )
                } catch (e: Exception) {
                    runOnUiThread {
                        statusToast.setText("Error: ${e.message}")
                        statusToast.show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        currentAgentManager?.unbindAgent()
        voiceRecognitionManager.stopVoiceRecognition()
    }
}



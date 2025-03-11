package xyz.block.gosling.features.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.R
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.shared.services.VoiceRecognitionService


@Composable
fun GoslingUI(
    context: Context,
    modifier: Modifier = Modifier,
    startVoice: Boolean = false,
    messages: List<ChatMessage>,
    onMessageAdded: (ChatMessage) -> Unit,
    onMessageRemoved: (ChatMessage) -> Unit
) {
    var isVoiceMode by remember { mutableStateOf(startVoice) }
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isOutputMode by remember { mutableStateOf(false) }
    var boundService by remember { mutableStateOf<Agent?>(null) }
    var showPresetQueries by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var voiceRecognitionManager by remember { mutableStateOf<VoiceRecognitionService?>(null) }

    // Scroll to bottom when returning to app
    LaunchedEffect(GoslingApplication.isMainActivityRunning) {
        if (GoslingApplication.isMainActivityRunning && messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                boundService = (service as Agent.AgentBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
    }

    DisposableEffect(context) {
        val intent = Intent(context, Agent::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    fun executeCommand(input: String) {
        scope.launch {
            onMessageAdded(ChatMessage(text = input, isUser = true))
            isOutputMode = true
            outputText = "Thinking..."
            onMessageAdded(ChatMessage(text = "Thinking...", isUser = false))
            try {
                // Create an AgentServiceManager to handle notifications
                val agentServiceManager = AgentServiceManager(context)

                // Bind to the Agent service and start it as a foreground service
                agentServiceManager.bindAndStartAgent { agent ->
                    // Set up status listener
                    agent.setStatusListener { status ->
                        outputText = when (status) {
                            is AgentStatus.Processing -> status.message
                            is AgentStatus.Success -> status.message
                            is AgentStatus.Error -> "Error: ${status.message}"
                        }
                        // Only add meaningful status updates
                        if (outputText.isNotBlank() &&
                            outputText != "Thinking..." &&
                            outputText != "Processing..." &&
                            messages.lastOrNull()?.text != outputText
                        ) {
                            if (messages.lastOrNull()?.text == "Thinking...") {
                                onMessageRemoved(messages.last())
                            }
                            onMessageAdded(ChatMessage(text = outputText, isUser = false))
                            // Scroll to bottom after adding message
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                        Log.d("Agent", "Status update: $status")
                        if (outputText.isNotEmpty() && outputText != "null") {
                            android.os.Handler(context.mainLooper).post {
                                Toast.makeText(
                                    context,
                                    outputText,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    scope.launch {
                        val response =
                            agent.processCommand(input, context, isNotificationReply = false)

                        // Only add the final response if it's not empty and not a duplicate
                        if (response.isNotBlank() && messages.lastOrNull()?.text != response) {
                            // Remove the last message if it was a thinking or processing message
                            if (messages.lastOrNull()?.text == "Thinking..." ||
                                messages.lastOrNull()?.text == "Processing..."
                            ) {
                                onMessageRemoved(messages.last())
                            }
                            onMessageAdded(ChatMessage(text = response, isUser = false))
                            // Scroll to bottom after adding final response
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Agent", "Error executing command", e)
                val errorMessage =
                    "Error: ${e.message ?: "Unknown error occurred"}\nPlease check your internet connection and try again."
                if (messages.lastOrNull()?.text == "Thinking...") {
                    onMessageRemoved(messages.last()) // Remove "Thinking..." message
                }
                onMessageAdded(ChatMessage(text = errorMessage, isUser = false))
                // Scroll to bottom after error message
                scope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
            inputText = ""
        }
    }

    LaunchedEffect(isVoiceMode) {
        if (isVoiceMode) {
            val activity = context as? Activity ?: return@LaunchedEffect

            // Initialize VoiceRecognitionManager if not already done
            if (voiceRecognitionManager == null) {
                voiceRecognitionManager = VoiceRecognitionService(context)
            }

            // Check and request permissions if needed
            if (!voiceRecognitionManager!!.hasRecordAudioPermission()) {
                voiceRecognitionManager!!.requestRecordAudioPermission(activity)
                isVoiceMode = false
                return@LaunchedEffect
            }

            // Start voice recognition with callback
            voiceRecognitionManager!!.startVoiceRecognition(
                object : VoiceRecognitionService.VoiceRecognitionCallback {
                    override fun onVoiceCommandReceived(command: String) {
                        inputText = command
                        executeCommand(command)
                    }

                    override fun onPartialResult(partialCommand: String) {
                        inputText = partialCommand
                    }

                    override fun onError(errorMessage: String) {
                        inputText = errorMessage
                        isVoiceMode = false
                    }

                    override fun onListening() {
                        inputText = "Listening..."
                    }

                    override fun onSpeechEnd() {
                        // Optional: Handle speech end if needed
                    }
                }
            )
        } else {
            voiceRecognitionManager?.stopVoiceRecognition()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isVoiceMode = !isVoiceMode
                                isOutputMode = false
                            },
                        ) {
                            Icon(
                                imageVector = if (isVoiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                                contentDescription = if (isVoiceMode) "Switch to Keyboard" else "Switch to Voice",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.gosling),
                            contentDescription = "Gosling Icon",
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            Log.d("Gosling", "Long press detected on logo")
                                            showPresetQueries = true
                                        }
                                    )
                                }
                        )
                    }

                    if (isVoiceMode) {
                        Text(
                            text = inputText.ifEmpty { "Listening..." },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Box {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                Log.d("Agent", "Long press detected")
                                                showPresetQueries = true
                                            }
                                        )
                                    },
                                placeholder = { Text("Type your request...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                )
                            )
                        }

                        Button(
                            onClick = { executeCommand(inputText) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = inputText.isNotBlank()
                        ) {
                            Text("Submit")
                        }
                    }
                }
            }
        }
    }
}


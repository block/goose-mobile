package xyz.block.gosling

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

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

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

    fun requestAudioPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
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
                        val response = agent.processCommand(input, context, isNotificationReply = false)

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
            requestAudioPermission(activity) { granted ->
                if (!granted) {
                    isVoiceMode = false
                    return@requestAudioPermission
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val voiceCommand =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                        if (!voiceCommand.isNullOrEmpty()) {
                            inputText = voiceCommand
                            executeCommand(voiceCommand)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                        if (!partial.isNullOrEmpty()) {
                            inputText = partial
                        }
                    }

                    override fun onError(error: Int) {
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. If you're using an emulator, please use the keyboard instead as emulators don't support voice input."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            else -> "Error: $error"
                        }
                        inputText = errorMessage
                        isVoiceMode = false
                    }

                    override fun onReadyForSpeech(params: Bundle?) {
                        inputText = "Listening..."
                    }

                    override fun onBeginningOfSpeech() {
                        inputText = "Listening..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
                speechRecognizer = recognizer
            }
        } else {
            speechRecognizer?.destroy()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Chat messages
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Bottom),
                    reverseLayout = true
                ) {
                    items(messages.asReversed()) { message ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (message.isUser) 32.dp else 0.dp,
                                    end = if (!message.isUser) 32.dp else 0.dp
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (message.isUser)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = message.text,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (message.isUser)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Bottom input panel
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
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    if (isVoiceMode) {
                        Text(
                            text = inputText.ifEmpty { "Listening..." },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type your request...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            )
                        )

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


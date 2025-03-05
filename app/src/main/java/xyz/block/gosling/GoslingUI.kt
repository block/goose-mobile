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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun GoslingUI(
    context: Context,
    modifier: Modifier = Modifier,
    startVoice: Boolean = false
) {
    var isVoiceMode by remember { mutableStateOf(startVoice) }
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isOutputMode by remember { mutableStateOf(false) }
    var boundService by remember { mutableStateOf<Agent?>(null) }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val scope = rememberCoroutineScope()

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
            isOutputMode = true
            outputText = "Thinking..."
            try {
                val service = boundService
                if (service == null) {
                    outputText = "Error: Service not connected"
                    return@launch
                }

                val response = async {
                    service.processCommand(input, context, isNotificationReply = false) { status ->
                        outputText = status
                        Log.d("Agent", "Status update: $status")
                    }
                }.await()
                outputText += "\n" + response
            } catch (e: Exception) {
                Log.e("Agent", "Error executing command", e)
                outputText += "\n\nError: ${e.message ?: "Unknown error occurred"}\n\nPlease check your internet connection and try again."
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

    LaunchedEffect(outputText) {
        OverlayService.getInstance()?.updateOverlayText(outputText)
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
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
                        modifier = Modifier.size(64.dp)
                    )
                }

                if (isOutputMode) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = outputText,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { isOutputMode = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("New Request")
                        }
                    }
                } else {
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


package xyz.block.gosling.features.launcher

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.ui.theme.GoslingTheme

/**
 * The main launcher screen composable that displays the home screen with a clock
 * and provides access to the app drawer via swipe-up gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // State for keyboard input drawer
    var showKeyboardDrawer by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    // Current time for the clock widget
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    // Update the clock every minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // Update every minute
            currentTime = getCurrentTime()
        }
    }

    // Load installed apps
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)

        appList = resolveInfoList.map { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo
            val appLabel = resolveInfo.loadLabel(pm).toString()
            val packageName = activityInfo.packageName
            val activityName = activityInfo.name
            val icon = resolveInfo.loadIcon(pm).toBitmap()

            AppInfo(appLabel, packageName, activityName, icon)
        }.sortedBy { it.label }
    }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
    )

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    // Draggable state for handling swipe gestures
    val draggableState = rememberDraggableState { delta ->
        // Negative delta means swipe up
        if (delta < -10) {
            coroutineScope.launch {
                bottomSheetState.expand()
            }
        } else if (delta > 10) {
            coroutineScope.launch {
                bottomSheetState.hide()
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            AppDrawer(
                apps = appList,
                onAppClick = { appInfo ->
                    val launchIntent = Intent()
                    launchIntent.setClassName(appInfo.packageName, appInfo.activityName)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)

                    // Hide the app drawer after launching an app
                    coroutineScope.launch {
                        bottomSheetState.hide()
                    }
                }
            )
        },
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {
            // Custom drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        // No additional action needed on drag stopped
                    }
                )
                .clickable {
                    // Also allow clicking anywhere to open the drawer
                    coroutineScope.launch {
                        if (bottomSheetState.currentValue == SheetValue.Hidden) {
                            bottomSheetState.expand()
                        } else {
                            bottomSheetState.hide()
                        }
                    }
                },
            color = MaterialTheme.colorScheme.background
        ) {
            // Home screen content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clock widget at the top
                Spacer(modifier = Modifier.height(48.dp))
                ClockWidget(currentTime = currentTime)

                Spacer(modifier = Modifier.weight(0.8f))

                // Input options (microphone and keyboard)
                InputOptions(
                    onMicrophoneClick = {
                        // Start voice recognition for agent commands
                        startVoiceRecognition(context)
                    },
                    onKeyboardClick = {
                        // Show keyboard input drawer
                        showKeyboardDrawer = true
                    }
                )

                Spacer(modifier = Modifier.weight(0.2f))

                // Instructions at the bottom
                Text(
                    text = "Swipe up or tap to open app drawer",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    // Keyboard input drawer
    if (showKeyboardDrawer) {
        KeyboardInputDrawer(
            value = textInput,
            onValueChange = { textInput = it },
            onDismiss = {
                showKeyboardDrawer = false
                textInput = ""
            },
            onSubmit = {
                if (textInput.isNotEmpty()) {
                    processAgentCommand(context, textInput)
                }
                showKeyboardDrawer = false
                textInput = ""
            }
        )
    }
}

/**
 * Preview function for the LauncherScreen.
 */
@Preview(showBackground = true)
@Composable
fun LauncherScreenPreview() {
    GoslingTheme {
        LauncherScreen()
    }
}

/**
 * Starts voice recognition to capture user commands for the Agent.
 */
private fun startVoiceRecognition(context: Context) {
    val activity = context as? Activity
    if (activity == null) {
        Toast.makeText(context, "Cannot start voice recognition", Toast.LENGTH_SHORT).show()
        return
    }

    // Check for permission
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED
    ) {

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            1
        )
        return
    }

    // Create speech recognizer
    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    // Set up recognition listener
    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val voiceCommand = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()

            if (voiceCommand != null && voiceCommand.isNotEmpty()) {
                // Process the command with the Agent
                processAgentCommand(context, voiceCommand)
            }

            speechRecognizer.destroy()
        }

        override fun onPartialResults(partialResults: Bundle) {
            // Handle partial results if needed
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Error: $error"
            }

            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            speechRecognizer.destroy()
        }

        override fun onReadyForSpeech(params: Bundle) {
            Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle) {}
    })

    // Start listening
    speechRecognizer.startListening(intent)
}

/**
 * Processes a command with the Agent service.
 */
private fun processAgentCommand(context: Context, command: String) {
    Toast.makeText(context, command, Toast.LENGTH_SHORT).show()

    // Bind to the Agent service
    val serviceIntent = Intent(context, Agent::class.java)
    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val agent = (service as Agent.AgentBinder).getService()

            // Create a final reference to the service connection
            val connection = this

            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    agent.processCommand(
                        userInput = command,
                        context = context,
                        isNotificationReply = false
                    ) { status ->
                        // Update UI based on status
                        when (status) {
                            is AgentStatus.Processing -> {
                                if (status.message.isEmpty() || status.message == "null") return@processCommand
                                // Show processing status
                                android.os.Handler(context.mainLooper).post {
                                    Toast.makeText(
                                        context,
                                        status.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            is AgentStatus.Success -> {
                                android.os.Handler(context.mainLooper).post {
                                    Toast.makeText(
                                        context,
                                        status.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            is AgentStatus.Error -> {
                                // Show error status
                                android.os.Handler(context.mainLooper).post {
                                    Toast.makeText(
                                        context,
                                        "Error: ${status.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle exceptions
                    android.os.Handler(context.mainLooper).post {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    // Unbind from the service on the main thread
                    android.os.Handler(context.mainLooper).post {
                        try {
                            context.unbindService(connection)
                        } catch (e: Exception) {
                            // Ignore unbinding errors
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Service disconnected
        }
    }

    // Bind to the service
    context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
}

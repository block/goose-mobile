package xyz.block.gosling.features.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.block.gosling.R
import xyz.block.gosling.features.agent.AgentServiceManager
import xyz.block.gosling.features.agent.AgentStatus
import xyz.block.gosling.features.agent.Conversation
import xyz.block.gosling.features.agent.getConversationTitle
import xyz.block.gosling.features.overlay.OverlayService
import xyz.block.gosling.shared.services.VoiceRecognitionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private val predefinedQueries = listOf(
    "What's the weather like?",
    "Add contact named James Gosling",
    "Show me the best beer garden in Berlin in maps",
    "Turn on flashlight",
    "Take a picture using the camera and attach that to a new email. Save the email in drafts"
)

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    isAccessibilityEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showPresetQueries by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val pulseAnim = rememberInfiniteTransition()
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // Process the selected image
            textInput = "Here's the photo I selected"
            // TODO: Handle the selected image URI
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                // Process the captured image
                textInput = "I just took a photo"
                // TODO: Handle the captured image URI
            }
        }
    }

    fun createImageUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir("Photos")
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun showImageOptions() {
        val activity = context as? Activity
        if (activity != null) {
            val options = arrayOf("Take Photo", "Choose from Gallery")
            android.app.AlertDialog.Builder(activity)
                .setTitle("Select Photo")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            photoUri = createImageUri()
                            photoUri?.let { uri ->
                                cameraLauncher.launch(uri)
                            }
                        }

                        1 -> imagePickerLauncher.launch("image/*")
                    }
                }
                .show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    BadgedBox(
                        badge = {
                            if (!isAccessibilityEnabled) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = modifier.absoluteOffset((-6).dp, 0.dp)
                                ) {
                                    Text("!")
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onNavigateToSettings
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("What can gosling do for you?") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.38f
                            ),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.38f
                            ),
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        minLines = 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showImageOptions() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Take or select photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                isRecording = true
                                startVoiceRecognition(context) { isRecording = false }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .scale(if (isRecording) scale else 1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = if (isRecording)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (textInput.isNotEmpty()) {
                                            processAgentCommand(
                                                context,
                                                textInput
                                            ) { message, isUser ->
                                                // Messages will be handled by the conversation manager now
                                            }
                                            textInput = ""
                                        }
                                    },
                                    onLongClick = { showPresetQueries = !showPresetQueries }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = if (textInput.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.goose),
                        contentDescription = "Goose",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(
                        start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                        top = paddingValues.calculateTopPadding(),
                        end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                        bottom = paddingValues.calculateBottomPadding() + 8.dp
                    ),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations) { conversation ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToConversation(conversation.id)
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = getConversationTitle(conversation),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = (DateUtils.getRelativeTimeSpanString(
                                            conversation.startTime,
                                            System.currentTimeMillis(),
                                            DateUtils.SECOND_IN_MILLIS,
                                            DateUtils.FORMAT_ABBREV_RELATIVE
                                        ).toString() + (conversation.endTime?.let { endTime ->
                                            val duration = endTime - conversation.startTime
                                            " (${formatDuration(duration)})"
                                        } ?: " (active)")),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "View conversation",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (showPresetQueries) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            horizontal = 16.dp,
                        )
                        .padding(bottom = paddingValues.calculateBottomPadding() + 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        predefinedQueries.forEach { query ->
                            Text(
                                text = query,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPresetQueries = false
                                        processAgentCommand(context, query) { message, isUser ->
                                            // Messages will be handled by the conversation manager now
                                        }
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Collect conversations from the agent
    LaunchedEffect(Unit) {
        val agentServiceManager = AgentServiceManager(context)
        agentServiceManager.bindAndStartAgent { agent ->
            CoroutineScope(Dispatchers.Main).launch {
                agent.conversationManager.conversations.collect { updatedConversations ->
                    conversations = updatedConversations
                }
            }
        }
    }
}

private fun startVoiceRecognition(
    context: Context,
    onRecordingComplete: () -> Unit
) {
    val activity = context as? Activity
    // Create a single Toast instance that will be reused
    val voiceToast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    if (activity == null) {
        voiceToast.setText("Cannot start voice recognition")
        voiceToast.show()
        onRecordingComplete()
        return
    }

    val voiceRecognitionManager = VoiceRecognitionService(context)

    if (!voiceRecognitionManager.hasRecordAudioPermission()) {
        voiceRecognitionManager.requestRecordAudioPermission(activity)
        onRecordingComplete()
        return
    }

    voiceRecognitionManager.startVoiceRecognition(
        object : VoiceRecognitionService.VoiceRecognitionCallback {
            override fun onVoiceCommandReceived(command: String) {
                processAgentCommand(context, command) { _, _ ->
                    // Messages will be handled by the conversation manager now
                }
                onRecordingComplete()
            }

            override fun onSpeechEnd() {
                onRecordingComplete()
            }

            override fun onError(errorMessage: String) {
                super.onError(errorMessage)
                voiceToast.setText(errorMessage)
                voiceToast.show()
                onRecordingComplete()
            }
        }
    )
}

private fun processAgentCommand(
    context: Context,
    command: String,
    onMessageReceived: (String, Boolean) -> Unit
) {
    val agentServiceManager = AgentServiceManager(context)
    val activity = context as MainActivity

    // Create a single Toast instance that will be reused
    val statusToast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    OverlayService.getInstance()?.apply {
        setIsPerformingAction(true)
        setActiveAgentManager(agentServiceManager)
    }

    agentServiceManager.bindAndStartAgent { agent ->
        agent.setStatusListener { status ->
            when (status) {
                is AgentStatus.Processing -> {
                    if (status.message.isEmpty() || status.message == "null") {
                        Log.d("MainScreen", "Ignoring empty/null processing message")
                        return@setStatusListener
                    }
                    android.os.Handler(context.mainLooper).post {
                        onMessageReceived(status.message, false)

                        statusToast.setText(status.message)
                        statusToast.show()

                        OverlayService.getInstance()?.updateStatus(status)
                    }
                }

                is AgentStatus.Success -> {
                    android.os.Handler(context.mainLooper).post {
                        onMessageReceived(status.message, false)

                        statusToast.setText(status.message)
                        statusToast.show()

                        OverlayService.getInstance()?.updateStatus(status)
                        OverlayService.getInstance()?.setIsPerformingAction(false)

                        // Create an intent to bring MainActivity to the foreground
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        context.startActivity(intent)
                    }
                }

                is AgentStatus.Error -> {
                    android.os.Handler(context.mainLooper).post {
                        val errorMessage = "Error: ${status.message}"
                        onMessageReceived(errorMessage, false)

                        statusToast.setText(errorMessage)
                        statusToast.show()

                        OverlayService.getInstance()?.updateStatus(status)
                        OverlayService.getInstance()?.setIsPerformingAction(false)
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                agent.processCommand(
                    userInput = command,
                    context = context,
                    isNotificationReply = false
                )
            } catch (e: Exception) {
                // Handle exceptions
                android.os.Handler(context.mainLooper).post {
                    val errorMessage = "Error: ${e.message}"
                    onMessageReceived(errorMessage, false)
                    activity.saveMessages(activity.loadMessages())
                    statusToast.setText(errorMessage)
                    statusToast.show()
                }
            }
        }
    }
}


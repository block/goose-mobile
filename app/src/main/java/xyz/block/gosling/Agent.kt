package xyz.block.gosling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import xyz.block.gosling.ToolHandler.callTool
import xyz.block.gosling.ToolHandler.getSerializableToolDefinitions
import xyz.block.gosling.SerializableToolDefinitions
import xyz.block.gosling.InternalToolCall
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow

class AgentException(message: String) : Exception(message)

sealed class AgentStatus {
    data class Processing(val message: String) : AgentStatus()
    data class Success(val message: String) : AgentStatus()
    data class Error(val message: String) : AgentStatus()
}

class Agent : Service() {
    private var job = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + job)
    private val binder = AgentBinder()
    private var isCancelled = false

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "agent_service"
        private const val NOTIFICATION_ID = 2
        private var instance: Agent? = null
        fun getInstance(): Agent? = instance
    }

    inner class AgentBinder : Binder() {
        fun getService(): Agent = this@Agent
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        instance = null
    }

    fun cancel() {
        isCancelled = true
        
        job.cancel()
        
        job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + job)
        
        updateNotification("Agent cancelled")
        OverlayService.getInstance()?.updateStatus(AgentStatus.Success("Agent cancelled"))
        
        OverlayService.getInstance()?.setPerformingAction(false)
    }

    fun isCancelled(): Boolean {
        return isCancelled
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gosling Agent")
            .setContentText("Processing commands")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val name = "Gosling Agent"
        val descriptionText = "Handles network operations and command processing"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    suspend fun processCommand(
        userInput: String,
        context: Context,
        isNotificationReply: Boolean,
        onStatusUpdate: (AgentStatus) -> Unit
    ): String {
        try {
            // Reset cancelled flag at the start of a new command
            isCancelled = false
            
            val availableIntents = IntentScanner.getAvailableIntents(context)
            val installedApps = availableIntents.joinToString("\n") { it.formatForLLM() }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels

            val role =
                if (isNotificationReply) "helping the user process android notifications" else "managing the users android phone"

            val systemMessage = """
                You are an assistant $role. The user does not have access to the phone. 
                You will autonomously complete complex tasks on the phone and report back to the user when 
                done. NEVER ask the user for additional information or choices - you must decide and act on your own.
                
                Your goal is to complete the requested task through any means necessary. If one approach doesn't work,
                try alternative methods until you succeed. Be persistent and creative in finding solutions.
                
                When you call a tool, tell the user about it. After each tool call you will see the state of the phone 
                by way of a screenshot and a ui hierarchy produced using 'adb shell uiautomator dump'. One or both 
                might be simplified or omitted to save space. Use this to verify your work.
                
                The phone has a screen resolution of ${width}x${height} pixels
                The phone has the following apps installed:
        
                $installedApps
                
                Before getting started, explicitly state the steps you want to take and which app(s) you want 
                use to accomplish that task. For example, open the contacts app to find out Joe's phone number. 
                
                After each step verify that the step was completed successfully by looking at the screen and 
                the ui hierarchy dump. If the step was not completed successfully, try to recover by:
                1. Trying a different approach
                2. Using a different app
                3. Looking for alternative UI elements
                4. Adjusting your interaction method
                
                When you start an app, make sure the app is in the state you expect it to be in. If it is not, 
                try to navigate to the correct state.
                
                After each tool call and before the next step, write down what you see on the screen that helped 
                you resolve this step. Keep iterating until you complete the task or have exhausted all possible approaches.
                
                Remember: DO NOT ask the user for help or additional information - you must solve the problem autonomously.
            """.trimIndent()

            val messages = mutableListOf(
                Message(
                    role = "system",
                    content = systemMessage
                ),
                Message(
                    role = "user",
                    content = userInput
                )
            )

            onStatusUpdate(AgentStatus.Processing("Thinking..."))

            return withContext(scope.coroutineContext) {
                var retryCount = 0
                val maxRetries = 3

                while (true) {
                    if (isCancelled) {
                        onStatusUpdate(AgentStatus.Success("Operation cancelled"))
                        return@withContext "Operation cancelled by user"
                    }
                    
                    var response: JSONObject?
                    try {
                        if (retryCount > 0) {
                            val delayMs = (2.0.pow(retryCount.toDouble()) * 1000).toLong()
                            delay(delayMs)
                            onStatusUpdate(AgentStatus.Processing("Retrying... (attempt ${retryCount + 1})"))
                        }

                        response = callLlm(messages, context)
                        retryCount = 0
                    } catch (e: AgentException) {
                        if (isCancelled) {
                            onStatusUpdate(AgentStatus.Success("Operation cancelled"))
                            return@withContext "Operation cancelled by user"
                        }
                        
                        retryCount++

                        if (retryCount >= maxRetries) {
                            val errorMsg = "Failed after $maxRetries attempts: ${e.message}"
                            onStatusUpdate(AgentStatus.Error(errorMsg))
                            return@withContext errorMsg
                        }
                        continue
                    }

                    try {
                        val (assistantReply, toolCalls) = when {
                            response.has("choices") -> {
                                val assistantMessage = response.getJSONArray("choices").getJSONObject(0)
                                    .getJSONObject("message")
                                val content = assistantMessage.optString("content", "Ok")
                                val tools = assistantMessage.optJSONArray("tool_calls")?.let {
                                    List(it.length()) { i -> ToolHandler.fromJson(it.getJSONObject(i)) }
                                }
                                Pair(content, tools)
                            }

                            response.has("candidates") -> {
                                val candidate = response.getJSONArray("candidates").getJSONObject(0)
                                val content = candidate.getJSONObject("content")
                                val text = content.getJSONArray("parts").getJSONObject(0)
                                    .optString("text", "Ok")

                                val tools = content.optJSONArray("parts")?.let { parts ->
                                    List(parts.length()) { i ->
                                        val part = parts.getJSONObject(i)
                                        if (part.has("functionCall")) {
                                            ToolHandler.fromJson(part)
                                        } else null
                                    }.filterNotNull()
                                }
                                Pair(text, tools)
                            }

                            else -> Pair("Unknown response format", null)
                        }

                        onStatusUpdate(AgentStatus.Processing(assistantReply))

                        if (isCancelled) {
                            onStatusUpdate(AgentStatus.Success("Operation cancelled"))
                            return@withContext "Operation cancelled by user"
                        }
                        
                        val toolResults = executeTools(toolCalls, context)
                        if (toolResults.isEmpty() || isCancelled) {
                            if (isCancelled) {
                                onStatusUpdate(AgentStatus.Success("Operation cancelled"))
                                return@withContext "Operation cancelled by user"
                            } else {
                                onStatusUpdate(AgentStatus.Success(assistantReply))
                                break
                            }
                        }

                        // Create a map of tool calls with their IDs
                        val toolCallsWithIds = toolCalls?.mapIndexed { index, toolCall ->
                            val toolCallId = toolResults[index]["tool_call_id"] ?: "call_${System.currentTimeMillis()}_$index"
                            toolCall to toolCallId
                        } ?: emptyList()

                        messages.add(
                            Message(
                                role = "assistant",
                                content = assistantReply,
                                toolCalls = toolCallsWithIds.map { (toolCall, id) ->
                                    ToolCall(
                                        id = id,
                                        function = ToolFunction(
                                            name = toolCall.name,
                                            arguments = toolCall.arguments.toString()
                                        )
                                    )
                                }
                            )
                        )

                        for (result in toolResults) {
                            messages.add(
                                Message(
                                    role = "tool",
                                    toolCallId = result["tool_call_id"].toString(),
                                    content = result["output"].toString()
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("Agent", "Error processing response", e)
                        val errorMsg = "Error processing response: ${e.message}"
                        onStatusUpdate(AgentStatus.Error(errorMsg))
                        return@withContext errorMsg
                    }
                }
                onStatusUpdate(AgentStatus.Success("Task completed successfully"))
                ""
            }
        } catch (e: Exception) {
            // Handle any unexpected exceptions
            Log.e("Agent", "Error executing command", e)
            
            // If it's a cancellation exception, handle it gracefully
            if (e is kotlinx.coroutines.CancellationException) {
                // Reset the job and scope to ensure future commands work
                job = SupervisorJob()
                scope = CoroutineScope(Dispatchers.IO + job)
                onStatusUpdate(AgentStatus.Success("Operation cancelled"))
                return "Operation cancelled by user"
            }
            
            // For other exceptions, report the error
            val errorMsg = "Error: ${e.message}"
            onStatusUpdate(AgentStatus.Error(errorMsg))
            return errorMsg
        }
    }

    fun handleNotification(
        packageName: String,
        title: String,
        content: String,
        category: String,
    ) {
        scope.launch {
            try {
                val prompt = """
                    Here's the notification:
                    App: $packageName
                    Title: $title
                    Content: $content
                    Category: $category
                    
                    Please analyze this notification and take appropriate action if needed.
                """.trimIndent()

                processCommand(
                    prompt,
                    this@Agent,
                    isNotificationReply = true
                ) { status ->
                    when (status) {
                        is AgentStatus.Processing -> updateNotification(status.message)
                        is AgentStatus.Success -> updateNotification(status.message)
                        is AgentStatus.Error -> updateNotification("Error: ${status.message}")
                    }
                }
            } catch (e: Exception) {
                // Handle any unexpected exceptions
                Log.e("Agent", "Error handling notification", e)
                
                // If it's a cancellation exception, handle it gracefully
                if (e is kotlinx.coroutines.CancellationException) {
                    // Reset the job and scope to ensure future commands work
                    job = SupervisorJob()
                    scope = CoroutineScope(Dispatchers.IO + job)
                    updateNotification("Operation cancelled")
                } else {
                    // For other exceptions, report the error
                    updateNotification("Error: ${e.message}")
                }
            }
        }
    }

    private fun updateNotification(status: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gosling Agent")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Update overlay with current status
        OverlayService.getInstance()?.updateStatus(AgentStatus.Processing(status))
    }

    private fun callLlm(messages: List<Message>, context: Context): JSONObject {
        val settings = SettingsManager(context)
        val model = AiModel.fromIdentifier(settings.llmModel)
        val apiKey = settings.apiKey

        val url = when (model.provider) {
            ModelProvider.OPENAI -> URL("https://api.openai.com/v1/chat/completions")
            ModelProvider.GEMINI -> URL("https://generativelanguage.googleapis.com/v1beta/models/${model.identifier}:generateContent?key=$apiKey")
        }

        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")

            if (model.provider == ModelProvider.OPENAI) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }

            connection.doOutput = true

            // Create a Json instance with pretty printing for debugging
            val json = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

            val requestBody = when (model.provider) {
                ModelProvider.OPENAI -> {
                    val toolDefinitions =
                        when (val result = getSerializableToolDefinitions(model.provider)) {
                            is SerializableToolDefinitions.OpenAITools -> result.definitions
                            else -> emptyList() // This should never happen for OpenAI
                        }

                    val openAIRequest = OpenAIRequest(
                        model = model.identifier,
                        messages = messages,
                        temperature = if (model.identifier != "o3-mini") 0.1 else null,
                        tools = toolDefinitions
                    )

                    json.encodeToString(openAIRequest)
                }

                ModelProvider.GEMINI -> {
                    // Combine all messages into a single text for Gemini
                    val combinedText = messages.joinToString("\n") {
                        "${it.role}: ${it.content}"
                    }

                    // Get serializable tool definitions directly
                    val tools = when (val result = getSerializableToolDefinitions(model.provider)) {
                        is SerializableToolDefinitions.GeminiTools -> result.tools
                        else -> emptyList() // This should never happen for Gemini
                    }

                    // Create the Gemini request
                    val geminiRequest = GeminiRequest(
                        contents = GeminiContent(
                            parts = listOf(GeminiPart(text = combinedText))
                        ),
                        tools = tools
                    )

                    json.encodeToString(geminiRequest)
                }
            }

            connection.outputStream.write(requestBody.toByteArray())

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw AgentException("Error calling LLM: $errorResponse")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }

            // Parse the response using Kotlinx Serialization for type safety
            when (model.provider) {
                ModelProvider.OPENAI -> {
                    json.decodeFromString<OpenAIResponse>(response)
                }

                ModelProvider.GEMINI -> {
                    json.decodeFromString<GeminiResponse>(response)
                }
            }

            // Return the original JSONObject for backward compatibility
            JSONObject(response)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("Unable to resolve host") == true -> {
                    // Check network connectivity
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val capabilities =
                        network?.let { connectivityManager.getNetworkCapabilities(it) }

                    if (network == null || capabilities == null) {
                        "Network error: No active network connection"
                    } else {
                        "Network error: DNS resolution failed. Will retry with new connection..."
                    }
                }

                e.message?.contains("timeout") == true -> "Request timed out. Please try again."
                else -> "Error calling LLM: ${e.message}"
            }
            throw AgentException(message)
        } finally {
            connection.disconnect()
        }
    }

    private fun executeTools(
        toolCalls: List<InternalToolCall>?,
        context: Context
    ): List<Map<String, String>> {
        if (toolCalls == null || isCancelled) return emptyList()

        return toolCalls.mapIndexed { index, toolCall ->
            if (isCancelled) {
                return@mapIndexed mapOf(
                    "tool_call_id" to "cancelled_${System.currentTimeMillis()}_$index",
                    "output" to "Operation cancelled by user"
                )
            }
            
            val toolCallId = "call_${System.currentTimeMillis()}_$index"  // Generate a unique ID
            val result = callTool(toolCall, context, GoslingAccessibilityService.getInstance())
            mapOf(
                "tool_call_id" to toolCallId,
                "output" to result
            )
        }
    }
}

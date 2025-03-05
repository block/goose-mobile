package xyz.block.gosling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AgentException(message: String) : Exception(message)

class Agent : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val binder = AgentBinder()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Gosling Agent"
            val descriptionText = "Handles network operations and command processing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    data class Message(
        val role: String,
        val content: Any?,
        val tool_call_id: String? = null,
        val name: String? = null,
        val tool_calls: List<Map<String, Any>>? = null
    )

    suspend fun processCommand(userInput: String, context: Context, isNotificationReply: Boolean, onStatusUpdate: (String) -> Unit): String {
        val availableIntents = IntentScanner.getAvailableIntents(context)
        val installedApps = availableIntents.joinToString("\n") { it.formatForLLM() }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val role = if (isNotificationReply) "helping the user process android notifications" else "managing the users android phone"

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
            Message(role = "system", content = systemMessage),
            Message(role = "user", content = userInput)
        )

        onStatusUpdate("Thinking...")

        return withContext(scope.coroutineContext) {
            var retryCount = 0
            val maxRetries = 3
            var lastError: Exception? = null

            while (true) {
                var response: JSONObject? = null
                try {
                    // Add exponential backoff delay for retries
                    if (retryCount > 0) {
                        val delayMs = (Math.pow(2.0, retryCount.toDouble()) * 1000).toLong()
                        delay(delayMs)
                        onStatusUpdate("Retrying... (attempt ${retryCount + 1})")
                    }

                    response = callLlm(messages, context)
                    retryCount = 0 // Reset retry count on successful call
                } catch (e: AgentException) {
                    lastError = e
                    retryCount++
                    
                    if (retryCount >= maxRetries) {
                        onStatusUpdate("Failed after $maxRetries attempts: ${e.message}")
                        return@withContext "Failed: ${e.message}"
                    }
                    continue
                }

                try {
                    val assistantMessage = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    val assistantReply = assistantMessage.optString("content", "Ok")

                    onStatusUpdate(assistantReply)

                    val toolResults = parseAndExecuteTool(response, context)
                    if (toolResults.isEmpty()) break

                    messages.add(
                        Message(
                            role = "assistant",
                            content = assistantMessage.optString("content"),
                            tool_calls = assistantMessage.optJSONArray("tool_calls")?.let {
                                List(it.length()) { i -> jsonObjectToMap(it.getJSONObject(i)) }
                            }
                        )
                    )

                    for (result in toolResults) {
                        messages.add(
                            Message(
                                role = "tool",
                                tool_call_id = result["tool_call_id"].toString(),
                                content = result["output"]
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Agent", "Error processing response", e)
                    onStatusUpdate("Error processing response: ${e.message}")
                    return@withContext "Error: ${e.message}"
                }
            }
            ""
        }
    }

    fun handleNotification(
        packageName: String,
        title: String,
        content: String,
        context: String,
        image: Bitmap?,
        timestamp: Long,
        category: String,
        actions: List<String>
    ) {
        scope.launch {
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
                updateNotification("Processing notification: $status")
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun callLlm(messages: List<Message>, context: Context): JSONObject {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection

        // Use the same preferences and keys as SettingsManager
        val sharedPrefs = context.getSharedPreferences("gosling_prefs", Context.MODE_PRIVATE)
        val model = sharedPrefs.getString("llm_model", "gpt-4o") ?: "gpt-4o"
        val apiKey = sharedPrefs.getString("api_key", "") ?: ""

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val messagesJsonArray = JSONArray()
            messages.forEach { message ->
                messagesJsonArray.put(messageToJson(message))
            }

            val requestBody = JSONObject()
            requestBody.put("model", model)
            requestBody.put("messages", messagesJsonArray)
            if (model != "o3-mini") {
                requestBody.put("temperature", 0.1)
            }
            requestBody.put("tools", getToolDefinitions().toJSONArray())

            connection.outputStream.write(requestBody.toString().toByteArray())

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw AgentException("Error calling LLM: $errorResponse")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("Unable to resolve host") == true -> {
                    // Check network connectivity
                    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
                    
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

    private fun parseAndExecuteTool(response: JSONObject, context: Context): List<Map<String, String>> {
        val assistantMessage = response.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")

        val toolCalls = assistantMessage.optJSONArray("tool_calls") ?: return emptyList()
        val toolResults = mutableListOf<Map<String, String>>()

        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.getJSONObject(i)
            val toolCallId = toolCall.getString("id")

            val result = callTool(toolCall, context, GoslingAccessibilityService.getInstance())
            toolResults.add(mapOf("tool_call_id" to toolCallId, "output" to result))
        }

        return toolResults
    }

    private fun messageToJson(message: Message): JSONObject {
        val json = JSONObject()
        json.put("role", message.role)
        if (message.content != null) {
            json.put("content", message.content)
        }
        message.tool_call_id?.let { json.put("tool_call_id", it) }
        message.name?.let { json.put("name", it) }
        message.tool_calls?.let {
            val toolCallsJson = JSONArray()
            it.forEach { toolCall ->
                toolCallsJson.put((toolCall as Map<String, Any>).toJSONObject())
            }
            json.put("tool_calls", toolCallsJson)
        }
        return json
    }

    private fun Map<String, Any>.toJSONObject(): JSONObject {
        val json = JSONObject()
        this.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> json.put(key, (value as Map<String, Any>).toJSONObject())
                is List<*> -> json.put(key, (value as List<Any>).toJSONArray())
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun List<Any>.toJSONArray(): JSONArray {
        val jsonArray = JSONArray()
        this.forEach { item ->
            when (item) {
                is Map<*, *> -> jsonArray.put((item as Map<String, Any>).toJSONObject())
                is List<*> -> jsonArray.put((item as List<Any>).toJSONArray())
                else -> jsonArray.put(item)
            }
        }
        return jsonArray
    }

    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = when (val value = jsonObject.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any> {
        return List(jsonArray.length()) { i ->
            when (val value = jsonArray.get(i)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
    }

}

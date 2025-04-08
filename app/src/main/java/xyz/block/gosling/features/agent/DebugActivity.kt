package xyz.block.gosling.features.agent

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import xyz.block.gosling.features.accessibility.GoslingAccessibilityService
import xyz.block.gosling.features.agent.ToolHandler.getUiHierarchy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugActivity : AppCompatActivity() {
    companion object {
        private const val COMMAND_OUTPUT_FILE = "command_result.txt"
        private const val LATEST_COMMAND_LINK = "latest_command_result.txt"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            "xyz.block.gosling.GET_UI_HIERARCHY" -> {
                val service = GoslingAccessibilityService.getInstance()
                if (service != null) {
                    val hierarchyText = getUiHierarchy(service, JSONObject())
                    Log.d("UiHierarchy", "CAPTURED:\n$hierarchyText")
                } else {
                    Log.e("UiHierarchy", "Service not running")
                }
            }

            "xyz.block.gosling.EXECUTE_COMMAND" -> {
                val command = intent.getStringExtra("command")
                Log.d("DebugActivity", "Executing command: $command")

                if (command != null) {
                    val agentServiceManager = AgentServiceManager(this)
                    agentServiceManager.bindAndStartAgent { agent ->
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Process the command
                                val result = agent.processCommand(
                                    userInput = command,
                                    context = this@DebugActivity,
                                    triggerType = Agent.TriggerType.MAIN
                                )
                                Log.d("DebugActivity", "Command executed successfully")
                                
                                // Get the full conversation content
                                val conversation = agent.conversationManager.currentConversation.firstOrNull()
                                if (conversation != null) {
                                    // Write the rich conversation content to file
                                    writeRichResultToFile(command, result.toString(), conversation)
                                } else {
                                    // Fallback to simple result if conversation is null
                                    writeResultToFile(command, result.toString())
                                }
                            } catch (e: Exception) {
                                Log.e("DebugActivity", "Error executing command: ${e.message}")
                                // Write error to file
                                writeResultToFile(command, "Error: ${e.message}")
                            }
                        }
                    }
                }
            }

            else -> {
                Log.e("DebugActivity", "Unknown action: ${intent.action}")
            }
        }

        finish()
    }
    
    /**
     * Writes the command result to a file in the app's external files directory.
     * Creates both a timestamped file and updates a "latest" file for easy access.
     * 
     * @param command The command that was executed
     * @param result The result of the command execution
     */
    private fun writeResultToFile(command: String, result: String) {
        try {
            // Create a timestamp for the filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "command_result_${timestamp}.txt"
            
            // Get the app's external files directory
            val externalDir = getExternalFilesDir(null)
            if (externalDir != null && externalDir.exists()) {
                // Create the result file with timestamp
                val resultFile = File(externalDir, filename)
                
                // Create the content with command and result
                val content = StringBuilder()
                    .append("Command: ").append(command).append("\n\n")
                    .append("Result:\n").append(result).append("\n")
                    .toString()
                
                // Write to the timestamped file
                resultFile.writeText(content)
                Log.d("DebugActivity", "Wrote result to file: ${resultFile.absolutePath}")
                
                // Also update the "latest" file for easy access
                val latestFile = File(externalDir, LATEST_COMMAND_LINK)
                latestFile.writeText(content)
                Log.d("DebugActivity", "Updated latest result file: ${latestFile.absolutePath}")
            } else {
                Log.e("DebugActivity", "External directory not available")
            }
        } catch (e: Exception) {
            Log.e("DebugActivity", "Error writing result to file: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Writes the rich conversation content to a file in the app's external files directory.
     * This includes all messages exchanged during the conversation, providing the full context
     * and rich information that would be displayed in the GUI.
     * 
     * @param command The command that was executed
     * @param result The simple result string
     * @param conversation The full conversation object with all messages
     */
    private fun writeRichResultToFile(command: String, result: String, conversation: Conversation) {
        try {
            // Create a timestamp for the filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "command_result_${timestamp}.txt"
            
            // Get the app's external files directory
            val externalDir = getExternalFilesDir(null)
            if (externalDir != null && externalDir.exists()) {
                // Create the result file with timestamp
                val resultFile = File(externalDir, filename)
                
                // Create a pretty JSON formatter
                val jsonFormatter = Json { 
                    prettyPrint = true 
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                }
                
                // Extract user and assistant messages for a cleaner output
                val relevantMessages = conversation.messages.filter { 
                    it.role == "user" || it.role == "assistant" || it.role == "tool"
                }
                
                // Build the content with command, result, and rich conversation
                val content = StringBuilder()
                    .append("Command: ").append(command).append("\n\n")
                    .append("Simple Result: ").append(result).append("\n\n")
                    .append("Rich Conversation Content:\n")
                
                // Add each message with its content
                relevantMessages.forEach { message ->
                    content.append("\n--- ${message.role.uppercase()} ---\n")
                    
                    // Handle different message content types
                    message.content?.forEach { contentItem ->
                        when (contentItem) {
                            is Content.Text -> content.append(contentItem.text).append("\n")
                            is Content.ImageUrl -> content.append("[Image: ${contentItem.imageUrl.url}]\n")
                        }
                    }
                    
                    // Add tool calls if present
                    message.toolCalls?.forEach { toolCall ->
                        content.append("\nTool Call: ${toolCall.function.name}\n")
                        content.append("Arguments: ${toolCall.function.arguments}\n")
                    }
                    
                    // Add a separator between messages
                    content.append("\n")
                }
                
                // Add the full JSON representation for programmatic use
                content.append("\n\n--- FULL JSON REPRESENTATION ---\n")
                content.append(jsonFormatter.encodeToString(conversation))
                
                // Write to the timestamped file
                resultFile.writeText(content.toString())
                Log.d("DebugActivity", "Wrote rich result to file: ${resultFile.absolutePath}")
                
                // Also update the "latest" file for easy access
                val latestFile = File(externalDir, LATEST_COMMAND_LINK)
                latestFile.writeText(content.toString())
                Log.d("DebugActivity", "Updated latest result file: ${latestFile.absolutePath}")
            } else {
                Log.e("DebugActivity", "External directory not available")
            }
        } catch (e: Exception) {
            Log.e("DebugActivity", "Error writing rich result to file: ${e.message}")
            e.printStackTrace()
            
            // Fall back to simple result writing
            writeResultToFile(command, result)
        }
    }
}

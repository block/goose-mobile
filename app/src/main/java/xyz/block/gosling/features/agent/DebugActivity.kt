package xyz.block.gosling.features.agent

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.block.gosling.features.accessibility.GoslingAccessibilityService
import xyz.block.gosling.features.agent.ToolHandler.getUiHierarchy

class DebugActivity : AppCompatActivity() {
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
                                agent.processCommand(
                                    userInput = command,
                                    context = this@DebugActivity,
                                    triggerType = Agent.TriggerType.MAIN
                                )
                                Log.d("DebugActivity", "Command executed successfully")
                            } catch (e: Exception) {
                                Log.e("DebugActivity", "Error executing command: ${e.message}")
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
}
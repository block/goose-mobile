package xyz.block.gosling.features.agent

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import xyz.block.gosling.features.accessibility.GoslingAccessibilityService
import xyz.block.gosling.features.agent.ToolHandler.getUiHierarchy

class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = GoslingAccessibilityService.getInstance()

        if (service != null) {
            val hierarchyText = getUiHierarchy(service, JSONObject())
            Log.d("UiHierarchy", "CAPTURED:\n$hierarchyText")
        } else {
            Log.e("UiHierarchy", "Service not running")
        }
        finish()
    }
}
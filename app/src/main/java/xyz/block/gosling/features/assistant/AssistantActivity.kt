package xyz.block.gosling.features.assistant

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.features.app.ChatMessage
import xyz.block.gosling.features.app.GoslingUI

class AssistantActivity : ComponentActivity() {
    private var isVoiceInteraction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the main activity is already running, finish this activity
        if (GoslingApplication.isMainActivityRunning) {
            finish()
            return
        }

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        window.attributes.apply {
            dimAmount = 0f
            format = android.graphics.PixelFormat.TRANSLUCENT
        }

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)

        isVoiceInteraction = intent?.action == Intent.ACTION_ASSIST

        setContent {
            val messages = remember { mutableStateListOf<ChatMessage>() }
            GoslingUI(
                context = this,
                startVoice = isVoiceInteraction,
                messages = messages,
                onMessageAdded = { messages.add(it) },
                onMessageRemoved = { messages.remove(it) }
            )
        }
    }
}


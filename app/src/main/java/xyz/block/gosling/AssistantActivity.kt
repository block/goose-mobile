package xyz.block.gosling

import GoslingUI
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class AssistantActivity : ComponentActivity() {
    private var isVoiceInteraction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            GoslingUI(
                context = this,
                startVoice = isVoiceInteraction
            )
        }
    }
}


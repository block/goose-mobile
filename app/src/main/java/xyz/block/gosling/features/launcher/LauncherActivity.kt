package xyz.block.gosling.features.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.OverlayService
import xyz.block.gosling.ui.theme.GoslingTheme

/**
 * LauncherActivity serves as a custom Android home screen (launcher).
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoslingTheme {
                LauncherScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GoslingApplication.isLauncherActivityRunning = true
        // Update overlay visibility
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onPause() {
        super.onPause()
        GoslingApplication.isLauncherActivityRunning = false
        // Update overlay visibility
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        GoslingApplication.isLauncherActivityRunning = false
        // Update overlay visibility
        OverlayService.getInstance()?.updateOverlayVisibility()
    }
} 

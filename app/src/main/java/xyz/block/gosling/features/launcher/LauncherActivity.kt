package xyz.block.gosling.features.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.features.overlay.OverlayService
import xyz.block.gosling.shared.theme.GoslingTheme

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
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onPause() {
        super.onPause()
        GoslingApplication.isLauncherActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        GoslingApplication.isLauncherActivityRunning = false
        OverlayService.getInstance()?.updateOverlayVisibility()
    }
} 

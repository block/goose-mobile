package xyz.block.goosemobile.features.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.block.goosemobile.GooseMobileApplication
import xyz.block.goosemobile.features.overlay.OverlayService
import xyz.block.goosemobile.shared.theme.GooseMobileTheme

/**
 * LauncherActivity serves as a custom Android home screen (launcher).
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GooseMobileTheme {
                LauncherScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GooseMobileApplication.isLauncherActivityRunning = true
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onPause() {
        super.onPause()
        GooseMobileApplication.isLauncherActivityRunning = false
        
        // Explicitly reset the overlay service state when leaving the launcher
        OverlayService.getInstance()?.setIsPerformingAction(false)
        
        OverlayService.getInstance()?.updateOverlayVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        GooseMobileApplication.isLauncherActivityRunning = false
        
        // Explicitly reset the overlay service state when leaving the launcher
        OverlayService.getInstance()?.setIsPerformingAction(false)
        
        OverlayService.getInstance()?.updateOverlayVisibility()
    }
} 

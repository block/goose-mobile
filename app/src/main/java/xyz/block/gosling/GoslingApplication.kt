package xyz.block.gosling

import android.app.Application

class GoslingApplication : Application() {
    companion object {
        var isMainActivityRunning = false
        var isLauncherActivityRunning = false
        
        // Global flag to enable/disable the overlay, defaults to disabled
        private var isOverlayEnabled = false
        
        // Helper function to check if any of our activities that should hide the overlay are running
        fun shouldHideOverlay(): Boolean {
            // If overlay is globally disabled, always hide it
            if (!isOverlayEnabled) {
                return true
            }
            // Otherwise, use the existing logic
            return isMainActivityRunning || isLauncherActivityRunning
        }
        
        // Enable the overlay globally
        fun enableOverlay() {
            isOverlayEnabled = true
            // Notify the overlay service to update visibility if it exists
            OverlayService.getInstance()?.updateOverlayVisibility()
        }
        
        // Disable the overlay globally
        fun disableOverlay() {
            isOverlayEnabled = false
            // Notify the overlay service to update visibility if it exists
            OverlayService.getInstance()?.updateOverlayVisibility()
        }
        
        // Check if overlay is globally enabled
        fun isOverlayEnabled(): Boolean {
            return isOverlayEnabled
        }
    }
}

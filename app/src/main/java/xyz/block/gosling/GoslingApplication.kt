package xyz.block.gosling

import android.app.Application
import xyz.block.gosling.features.overlay.OverlayService

class GoslingApplication : Application() {
    companion object {
        var isMainActivityRunning = false
        var isLauncherActivityRunning = false

        private var isOverlayEnabled = true

        fun shouldHideOverlay(): Boolean {
            if (!isOverlayEnabled) {
                return true
            }

            // Only hide overlay when we're in our own app
            return (isMainActivityRunning || isLauncherActivityRunning)
        }

        fun disableOverlay() {
            isOverlayEnabled = false
            // Notify the overlay service to update visibility if it exists
            OverlayService.getInstance()?.updateOverlayVisibility()
        }

        fun enableOverlay() {
            isOverlayEnabled = true
            // Notify the overlay service to update visibility if it exists
            OverlayService.getInstance()?.updateOverlayVisibility()
        }
    }
}

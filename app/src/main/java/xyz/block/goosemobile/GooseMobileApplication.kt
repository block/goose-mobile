package xyz.block.goosemobile

import android.app.Application
import xyz.block.goosemobile.features.overlay.OverlayService

class GooseMobileApplication : Application() {
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

        fun enableOverlay() {
            isOverlayEnabled = true
            // Notify the overlay service to update visibility if it exists
            OverlayService.getInstance()?.updateOverlayVisibility()
        }
    }
}

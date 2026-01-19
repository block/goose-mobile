package xyz.block.gosling.features.launcher

/**
 * Data class representing an application installed on the device.
 * Contains information needed to display and launch the app.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: android.graphics.Bitmap?
) 

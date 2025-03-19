package xyz.block.gosling.features.screenshot

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

/**
 * Manages screenshot detection functionality.
 */
class ScreenshotManager(private val context: Context) {
    companion object {
        private const val TAG = "ScreenshotManager"
    }

    private var lastScreenshotDate: Long = System.currentTimeMillis()
    private var onScreenshotListener: ((Uri) -> Unit)? = null

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            queryLatestScreenshot()
        }
    }

    /**
     * Sets a listener to be called when a screenshot is detected
     */
    fun setOnScreenshotListener(listener: (Uri) -> Unit) {
        onScreenshotListener = listener
    }

    /**
     * Starts monitoring for screenshots
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting screenshot monitoring")
        lastScreenshotDate = System.currentTimeMillis()
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    /**
     * Stops monitoring for screenshots
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping screenshot monitoring")
        context.contentResolver.unregisterContentObserver(contentObserver)
    }

    private fun queryLatestScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf((lastScreenshotDate / 1000).toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val relativePathColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val displayName = cursor.getString(displayNameColumn)
                val relativePath = cursor.getString(relativePathColumn)

                lastScreenshotDate = dateAdded * 1000

                if (isScreenshot(relativePath, displayName)) {
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    Log.d(TAG, "Screenshot detected: $contentUri")
                    onScreenshotListener?.invoke(contentUri)
                }
            }
        }
    }

    private fun isScreenshot(path: String, name: String): Boolean {
        return path.contains("screenshot", ignoreCase = true) ||
                name.contains("screenshot", ignoreCase = true)
    }
}
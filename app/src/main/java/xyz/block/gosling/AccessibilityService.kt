package xyz.block.gosling

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class GoslingAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: GoslingAccessibilityService? = null
        fun getInstance(): GoslingAccessibilityService? = instance
        private const val NOTIFICATION_CHANNEL_ID = "gosling_service"
        private const val NOTIFICATION_ID = 1
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Set up service info with all capabilities enabled
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        serviceInfo = info
        startForegroundService()
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gosling Assistant")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val name = "Gosling Service"
        val descriptionText = "Keeps Gosling running in background"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (!SettingsManager(this).shouldProcessNotifications) return

        val agent = Agent.getInstance() ?: return
        val parcelableData = event.parcelableData
        if (parcelableData is Notification) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName == "xyz.block.gosling") return

            val rawText = event.text.joinToString(" ")
            val extras = parcelableData.extras

            if (extras == null) {
                agent.handleNotification(
                    packageName = packageName,
                    title = "",
                    content = rawText,
                    context = "",
                    image = null,
                    timestamp = parcelableData.`when`,
                    category = parcelableData.category ?: "",
                    actions = emptyList()
                )
                return
            }

            val title = extras.getString(Notification.EXTRA_TITLE_BIG)
                ?: extras.getString(Notification.EXTRA_TITLE)
                ?: ""

            val content = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: rawText

            val context = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
                ?: ""

            val largeIcon = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
            val picture = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
            val image = picture ?: largeIcon

            val actions = parcelableData.actions?.map { action ->
                action.title.toString()
            } ?: emptyList()

            agent.handleNotification(
                packageName = packageName,
                title = title,
                content = content,
                context = context,
                image = image,
                timestamp = parcelableData.`when`,
                category = parcelableData.category ?: "",
                actions = actions
            )
        }
    }

    override fun onInterrupt() {
        // Handle interruptions if needed
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

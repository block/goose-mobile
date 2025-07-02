package xyz.block.goosemobile.features.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import xyz.block.goosemobile.R
import xyz.block.goosemobile.features.agent.Agent
import xyz.block.goosemobile.features.app.MainActivity
import xyz.block.goosemobile.features.settings.SettingsStore

class GooseMobileAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: GooseMobileAccessibilityService? = null
        fun getInstance(): GooseMobileAccessibilityService? = instance
        private const val NOTIFICATION_CHANNEL_ID = "goose_mobile_service"
        private const val NOTIFICATION_ID = 1
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Set up service info with notification monitoring only
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags =
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }

        serviceInfo = info
        startForegroundService()
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Goose Mobile Assistant").setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pendingIntent)
            .setOngoing(true).build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val name = "Goose Mobile Service"
        val descriptionText = "Keeps Goose Mobile running in background"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (!SettingsStore(this).shouldProcessNotifications) return

        val agent = Agent.getInstance() ?: return
        val parcelableData = event.parcelableData
        if (parcelableData is Notification) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName == "xyz.block.goosemobile") return

            val agentServiceManager = xyz.block.goosemobile.features.agent.AgentServiceManager(this)

            agent.setStatusListener { status ->
                agentServiceManager.updateNotification(status)
            }

            val rawText = event.text.joinToString(" ")
            val extras = parcelableData.extras

            if (extras == null) {
                agent.handleNotification(
                    packageName = packageName,
                    title = "",
                    content = rawText,
                    category = parcelableData.category ?: "",
                )
                return
            }

            val title = extras.getString(Notification.EXTRA_TITLE_BIG) ?: extras.getString(
                Notification.EXTRA_TITLE
            ) ?: ""

            val content = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: rawText

            agent.handleNotification(
                packageName = packageName,
                title = title,
                content = content,
                category = parcelableData.category ?: "",
            )
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

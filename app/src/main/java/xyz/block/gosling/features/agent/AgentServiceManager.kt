package xyz.block.gosling.features.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import xyz.block.gosling.R
import xyz.block.gosling.features.app.MainActivity
import xyz.block.gosling.features.settings.SettingsStore

/**
 * Manages the Agent service lifecycle and notifications.
 * This class is responsible for starting the Agent as a foreground service
 * and updating notifications based on Agent status.
 */
class AgentServiceManager(private val context: Context) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "agent_service"
        const val NOTIFICATION_ID = 2
        private const val TAG = "AgentServiceManager"
    }

    private var serviceConnection: ServiceConnection? = null
    private var isBound = false
    private val screenshotManager = ScreenshotManager(context)

    init {
        createNotificationChannel()

        screenshotManager.setOnScreenshotListener { uri ->
            val agent = Agent.getInstance()
            if (agent != null) {
                Log.d(TAG, "Screenshot detected, processing with agent")
                val settings = SettingsStore(context)
                if (settings.handleScreenshots && settings.screenshotHandlingPreferences != "") {
                    agent.processScreenshot(uri, settings.screenshotHandlingPreferences)
                }
            } else {
                Log.d(TAG, "Screenshot detected but agent is not available")
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Goose Mobile Agent"
        val descriptionText = "Handles network operations and command processing"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun startAgentForeground(agent: Agent) {
        val notification = createNotification("Processing commands")
        agent.startForeground(NOTIFICATION_ID, notification)
    }

    fun bindAndStartAgent(callback: (Agent) -> Unit) {
        if (isBound) {
            Log.d(TAG, "Service is already bound, skipping bind")
            return
        }

        val serviceIntent = Intent(context, Agent::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val agent = (service as Agent.AgentBinder).getService()

                startAgentForeground(agent)
                screenshotManager.startMonitoring()

                agent.setStatusListener { status ->
                    updateNotification(status)
                }

                callback(agent)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isBound = false
                serviceConnection = null
            }
        }

        context.startForegroundService(serviceIntent)
        val bound =
            context.bindService(serviceIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        isBound = bound
        Log.d(TAG, "Service bind attempt result: $bound")
    }

    fun unbindAgent() {
        if (isBound && serviceConnection != null) {
            try {
                context.unbindService(serviceConnection!!)
                screenshotManager.cleanup()
                Log.d(TAG, "Service unbound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service: ${e.message}")
            }
            isBound = false
            serviceConnection = null
        }
    }

    fun updateNotification(status: AgentStatus) {
        val message = when (status) {
            is AgentStatus.Processing -> status.message
            is AgentStatus.Success -> status.message
            is AgentStatus.Error -> "Error: ${status.message}"
        }

        val notification = createNotification(message)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Goose Agent")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
} 

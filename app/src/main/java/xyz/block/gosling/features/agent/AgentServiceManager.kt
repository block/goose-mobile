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
import androidx.core.app.NotificationCompat
import xyz.block.gosling.R
import xyz.block.gosling.features.app.MainActivity

/**
 * Manages the Agent service lifecycle and notifications.
 * This class is responsible for starting the Agent as a foreground service
 * and updating notifications based on Agent status.
 */
class AgentServiceManager(private val context: Context) {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "agent_service"
        const val NOTIFICATION_ID = 2
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for the Agent service
     */
    private fun createNotificationChannel() {
        val name = "Gosling Agent"
        val descriptionText = "Handles network operations and command processing"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Starts the Agent as a foreground service
     */
    fun startAgentForeground(agent: Agent) {
        val notification = createNotification("Processing commands")
        agent.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Binds to the Agent service and starts it as a foreground service
     */
    fun bindAndStartAgent(callback: (Agent) -> Unit) {
        val serviceIntent = Intent(context, Agent::class.java)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val agent = (service as Agent.AgentBinder).getService()

                // Start as foreground service
                startAgentForeground(agent)

                // Set up status listener
                agent.setStatusListener { status ->
                    updateNotification(status)
                }

                // Call the callback with the agent
                callback(agent)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Service disconnected
            }
        }

        // Start and bind to the service
        context.startForegroundService(serviceIntent)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Updates the notification based on Agent status
     */
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

    /**
     * Creates a notification with the given message
     */
    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gosling Agent")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
} 

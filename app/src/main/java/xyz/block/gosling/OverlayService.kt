package xyz.block.gosling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentStatus
import java.lang.ref.WeakReference

class OverlayService : Service() {
    companion object {
        private var instance: WeakReference<OverlayService>? = null
        fun getInstance(): OverlayService? = instance?.get()
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayText: TextView? = null
    private var overlayButton: Button? = null
    private var overlayCancelButton: Button? = null
    private lateinit var params: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var currentStatus: String = "Ready"
    private var isPerformingAction: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Ensure overlay is disabled by default when service starts
        // (This is redundant since the flag defaults to false, but makes it explicit)
        GoslingApplication.disableOverlay()

        // Inflate the overlay view
        val tempParent = LinearLayout(this).apply {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_layout, tempParent, false)
        overlayText = overlayView?.findViewById(R.id.overlay_text)
        overlayButton = overlayView?.findViewById(R.id.overlay_button)
        overlayCancelButton = overlayView?.findViewById(R.id.overlay_cancel_button)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 150  // Position it higher up
            windowAnimations = 0  // Disable animations to prevent z-order issues during transitions
        }

        // Set up touch listener for dragging
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // If the touch hasn't moved much, consider it a click
                    if (kotlin.math.abs(event.rawX - initialTouchX) < 10 &&
                        kotlin.math.abs(event.rawY - initialTouchY) < 10
                    ) {
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        // Set up click listener to show current status
        overlayView?.setOnClickListener {
            updateStatus(AgentStatus.Processing(currentStatus))
        }

        // Set up button click listener
        overlayButton?.setOnClickListener {
            val activityManager =
                getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = activityManager.getRunningTasks(1)
            if (tasks.isNotEmpty() && tasks[0].topActivity?.packageName == packageName) {
                activityManager.moveTaskToFront(
                    tasks[0].id,
                    android.app.ActivityManager.MOVE_TASK_WITH_HOME
                )
            }
        }

        // Set up cancel button click listener
        overlayCancelButton?.setOnClickListener {
            // Make the cancel button more visible to indicate it's being pressed
            overlayCancelButton?.alpha = 0.5f
            
            // Disable the button to prevent multiple clicks
            overlayCancelButton?.isEnabled = false
            
            // Cancel the agent
            Agent.getInstance()?.cancel()
            
            // Update the UI to show cancellation is in progress
            updateStatus(AgentStatus.Processing("Cancelling operation..."))
            
            // Reset the button appearance and state after a short delay
            overlayCancelButton?.postDelayed({
                overlayCancelButton?.alpha = 1.0f
                overlayCancelButton?.isEnabled = true
                updateStatus(AgentStatus.Success("Agent cancelled"))
            }, 500)
        }

        // Add the view and update its visibility based on app state
        windowManager.addView(overlayView, params)
        updateOverlayVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        instance?.clear()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "overlay_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("Overlay Running")
            .setContentText("Your overlay is active")
            .setSmallIcon(R.mipmap.ic_launcher).build()
    }

    fun updateStatus(status: AgentStatus) {
        // Convert status to display text and determine button visibility
        val (displayText, isDone) = when (status) {
            is AgentStatus.Processing -> {
                // Special case for cancellation message
                if (status.message.contains("cancel", ignoreCase = true)) {
                    Pair(status.message, false)
                } else {
                    Pair(status.message, false)
                }
            }
            is AgentStatus.Success -> Pair(status.message, true)
            is AgentStatus.Error -> Pair(status.message, true)
        }

        android.util.Log.d("Gosling", "updateStatus called with status: $status, isDone: $isDone")

        // Ignore null, empty, or "null" string messages
        if (displayText.isBlank() || displayText.contains("null") || displayText.trim().isEmpty()) {
            android.util.Log.d("Gosling", "Ignoring empty or null message")
            return
        }

        // Ignore generic processing/thinking messages unless they're about cancellation
        if ((displayText == "Processing..." || displayText == "Thinking...") && 
            !displayText.contains("cancel", ignoreCase = true)) {
            android.util.Log.d("Gosling", "Ignoring generic processing message")
            return
        }

        currentStatus = displayText
        overlayText?.post {
            overlayText?.text = displayText.take(600)
            android.util.Log.d(
                "Gosling",
                "Setting button visibility to: ${if (isDone) "VISIBLE" else "GONE"}"
            )
            overlayButton?.visibility = if (isDone) View.VISIBLE else View.GONE
            
            // Always show cancel button during processing, hide when done
            // But don't show it during cancellation
            val showCancelButton = !isDone && !displayText.contains("cancel", ignoreCase = true)
            overlayCancelButton?.visibility = if (showCancelButton) View.VISIBLE else View.GONE
            
            android.util.Log.d("Gosling", "Button visibility is now: ${overlayButton?.visibility}")
            android.util.Log.d("Gosling", "Button reference exists: ${overlayButton != null}")
        }
        updateOverlayVisibility()
    }

    fun updateOverlayVisibility() {
        overlayView?.post {
            val isGloballyEnabled = !GoslingApplication.shouldHideOverlay()
            val shouldShow = isGloballyEnabled && !isPerformingAction
            val newVisibility = if (shouldShow) View.VISIBLE else View.GONE
            android.util.Log.d(
                "Gosling",
                "updateOverlayVisibility: globallyEnabled=$isGloballyEnabled, isPerformingAction=$isPerformingAction, shouldShow=$shouldShow"
            )
            overlayView?.visibility = newVisibility
            android.util.Log.d("Gosling", "Overlay visibility set to: $newVisibility")
        }
    }

    fun setPerformingAction(performing: Boolean) {
        android.util.Log.d("Gosling", "setPerformingAction: $performing")
        isPerformingAction = performing
        updateOverlayVisibility()
    }
}

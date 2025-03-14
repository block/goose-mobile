package xyz.block.gosling.features.overlay

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
import xyz.block.gosling.GoslingApplication
import xyz.block.gosling.R
import xyz.block.gosling.features.agent.Agent
import xyz.block.gosling.features.agent.AgentServiceManager
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
    private var overlayCancelButton: Button? = null
    private lateinit var params: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var currentStatus: String = "Ready"
    private var isPerformingAction: Boolean = false
    private var activeAgentManager: AgentServiceManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Enable overlay by default when service starts
        GoslingApplication.enableOverlay()

        // Inflate the overlay view
        val tempParent = LinearLayout(this).apply {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_layout, tempParent, false)
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
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 150
            windowAnimations = 0

            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED

            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
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

        overlayCancelButton?.setOnClickListener {
            overlayCancelButton?.alpha = 0.5f
            overlayCancelButton?.isEnabled = false

            val agent = Agent.getInstance()
            agent?.cancel()

            updateStatus(AgentStatus.Processing("Cancelling operation..."))

            overlayCancelButton?.postDelayed({
                overlayCancelButton?.alpha = 1.0f
                overlayCancelButton?.isEnabled = true
                updateStatus(AgentStatus.Success("Agent cancelled"))
            }, 500)
        }

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
        activeAgentManager = null
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

        // Update the current status
        currentStatus = displayText
        overlayText?.post {
            overlayText?.text = displayText.take(600)
            overlayView?.visibility = View.VISIBLE

            val showCancelButton = !isDone && !displayText.contains("cancel", ignoreCase = true)
            overlayCancelButton?.visibility = if (showCancelButton) View.VISIBLE else View.GONE

            // Set processing state based on agent status
            setIsPerformingAction(!isDone)

            if (isDone) {
                overlayView?.postDelayed({
                    overlayView?.visibility = View.GONE
                }, 3000)
            }
        }
    }

    fun updateOverlayVisibility() {
        overlayView?.post {
            val shouldShow = !GoslingApplication.shouldHideOverlay() && isPerformingAction
            val newVisibility = if (shouldShow) View.VISIBLE else View.GONE

            if (shouldShow && overlayView != null) {
                try {
                    try {
                        windowManager.removeView(overlayView)
                    } catch (e: IllegalArgumentException) {
                        // View was not attached, which is fine
                    }

                    // Add with updated params
                    params.apply {
                        // Ensure we're using the highest allowed window type
                        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        flags = flags or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    }

                    windowManager.addView(overlayView, params)
                    overlayView?.visibility = View.VISIBLE
                    overlayView?.bringToFront()
                    overlayView?.requestLayout()
                    overlayView?.postDelayed({
                        if (overlayView?.visibility != View.VISIBLE) {
                            updateOverlayVisibility()
                        }
                    }, 100)

                } catch (e: Exception) {
                    android.util.Log.e("Gosling", "Error updating overlay view", e)
                }
            } else {
                overlayView?.visibility = View.GONE
            }

            android.util.Log.d("Gosling", "Overlay visibility set to: $newVisibility")
        }
    }

    fun setIsPerformingAction(isPerformingAction: Boolean) {
        this.isPerformingAction = isPerformingAction
        updateOverlayVisibility()
    }

    fun setActiveAgentManager(manager: AgentServiceManager) {
        activeAgentManager = manager
    }
}

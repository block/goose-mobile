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
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

class OverlayService : Service() {
    companion object {
        private var instance: WeakReference<OverlayService>? = null
        fun getInstance(): OverlayService? = instance?.get()
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private var overlayText: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Inflate the overlay view
        val tempParent = LinearLayout(this).apply {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_layout, tempParent, false) as LinearLayout
        overlayText = overlayView?.findViewById(R.id.overlay_text)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 150  // Position it higher up
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

        windowManager.addView(overlayView, params)
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

    fun updateOverlayText(text: String?) {
        if (text.isNullOrBlank() || text == "null") return

        overlayText?.post {
            overlayText?.text = text
        }
    }
}

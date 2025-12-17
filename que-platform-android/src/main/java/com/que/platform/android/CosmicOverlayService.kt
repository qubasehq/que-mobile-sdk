package com.que.platform.android

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import com.que.core.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect

/**
 * Floating overlay service that shows cosmic wave animation on top of all apps.
 * Displays agent status and logs in real-time.
 */
class CosmicOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var cosmicWave: CosmicWaveView? = null
    private var statusText: TextView? = null
    private var logsText: TextView? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val CHANNEL_ID = "cosmic_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "CosmicOverlay"
        val agentState = MutableStateFlow<AgentState>(AgentState.Idle)
        
        private var instance: CosmicOverlayService? = null
        
        fun updateState(state: AgentState) {
            agentState.value = state
        }
        
        fun addLog(message: String) {
            instance?.appendLog(message)
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "CosmicOverlayService created")
        
        // Create notification channel for foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cosmic Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Create notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Que Agent Active")
            .setContentText("Cosmic overlay is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Start foreground immediately to prevent crash
        startForeground(NOTIFICATION_ID, notification)

        createOverlay()
        
        // Observe agent state with debounce to prevent UI flicker
        scope.launch {
            agentState
                .debounce(100)
                .collect { state ->
                    updateOverlayForState(state)
                }
        }
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 50 // Slight offset from bottom edge

        // Create overlay layout
        overlayView = createOverlayLayout()
        
        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay added to window at BOTTOM")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun createOverlayLayout(): View {
        // For now, create a simple layout programmatically
        // In production, you'd inflate from XML
        val layout = android.widget.FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Transparent background, maybe a slight gradient if needed, but user wants simple
            setBackgroundColor(android.graphics.Color.TRANSPARENT) 
            setPadding(0, 0, 0, 0)
        }

        // Add cosmic wave - MAKE IT SMALL AT BOTTOM
        cosmicWave = CosmicWaveView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                150 // Height in pixels for the wave strip
            )
            (layoutParams as android.widget.FrameLayout.LayoutParams).gravity = Gravity.BOTTOM
            alpha = 0.8f
        }
        layout.addView(cosmicWave)

        // Content Container (Text)
        val contentLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // Align to bottom, but above the wave slightly if needed, or just container logic
            (layoutParams as android.widget.FrameLayout.LayoutParams).gravity = Gravity.BOTTOM
            setPadding(24, 24, 24, 160) // Bottom padding to clear the 150px wave a bit or overlap nicely
            
            // Semi-transparent background for text readablity
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
        }

        // Title (Small)
        val title = TextView(this).apply {
            text = "QUE"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        contentLayout.addView(title)

        // Status (Prominent)
        statusText = TextView(this).apply {
            text = "Idle"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#00FF88"))
            setPadding(0, 4, 0, 4)
        }
        contentLayout.addView(statusText)

        // Logs (Limited Height)
        val logsContainer = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                200 // Max height for logs (pixels) - keeps it small
            )
        }
        
        logsText = TextView(this).apply {
            text = "..."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        logsContainer.addView(logsText)
        contentLayout.addView(logsContainer)

        layout.addView(contentLayout)
        return layout
    }

    private fun updateOverlayForState(state: AgentState) {
        when (state) {
            is AgentState.Idle -> {
                statusText?.text = "Idle"
                cosmicWave?.setIntensity(0.2f)
            }
            is AgentState.Perceiving -> {
                statusText?.text = "Perceiving..."
                cosmicWave?.setIntensity(0.5f)
            }
            is AgentState.Thinking -> {
                statusText?.text = "Thinking..."
                cosmicWave?.setIntensity(0.8f)
            }
            is AgentState.Acting -> {
                statusText?.text = "${state.actionDescription}"
                cosmicWave?.setIntensity(1.0f)
            }
            is AgentState.Finished -> {
                statusText?.text = "${state.result}"
                cosmicWave?.setIntensity(0.3f)
            }
            is AgentState.Error -> {
                statusText?.text = "${state.message}"
                cosmicWave?.setIntensity(0.1f)
            }
        }
    }

    private fun appendLog(message: String) {
        scope.launch(Dispatchers.Main) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logsText?.append("[$timestamp] $message\n")
            
            // Auto-scroll
            val parent = logsText?.parent as? android.widget.ScrollView
            parent?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                Log.d(TAG, "Overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

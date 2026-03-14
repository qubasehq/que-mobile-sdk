package com.que.platform.android.service
import com.que.core.model.AgentState
import com.que.core.service.Agent
import com.que.platform.android.overlay.CosmicWaveView
import com.que.platform.android.overlay.QueMascotView

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
    private var contentLayout: View? = null
    private var toggleButton: View? = null
    private var isExpanded = false
    
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

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 0 // Top edge

        // Create overlay layout
        overlayView = createOverlayLayout()
        
        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay added to window at TOP")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun createOverlayLayout(): View {
        val root = android.widget.FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }

        // Add cosmic wave - TOP GLOW (Thin)
        cosmicWave = CosmicWaveView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                60 // Thin glow strip
            )
            (layoutParams as android.widget.FrameLayout.LayoutParams).gravity = Gravity.TOP
            alpha = 0.7f
        }
        root.addView(cosmicWave)

        // Pulsing Mascot Badge
        toggleButton = QueMascotView(this).apply {
            val size = 60 // Slightly larger for mascot
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 5 // Center it on the glow strip
            }
            setOnClickListener {
                toggleExpanded()
            }
        }
        root.addView(toggleButton)

        // Content Container (Text) - EXPANDS DOWNWARDS
        contentLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 70 // Below the glow and badge
            }
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#EE000000")) // Slightly more opaque for top
            visibility = View.GONE
        }

        // Title
        val title = TextView(this).apply {
            text = "AGENT STATUS & LOGS"
            textSize = 10f
            setTextColor(android.graphics.Color.GRAY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        (contentLayout as android.widget.LinearLayout).addView(title)

        // Status
        statusText = TextView(this).apply {
            text = "Idle"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#00FF88"))
            setPadding(0, 4, 0, 4)
        }
        (contentLayout as android.widget.LinearLayout).addView(statusText)

        // Logs
        val logsContainer = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }
        
        logsText = TextView(this).apply {
            text = "..."
            textSize = 11f
            setTextColor(android.graphics.Color.LTGRAY)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        logsContainer.addView(logsText)
        (contentLayout as android.widget.LinearLayout).addView(logsContainer)

        root.addView(contentLayout)
        return root
    }

    private fun startBadgePulse() {
        toggleButton?.let { view ->
            val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f)
            val alpha = android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.7f, 1f, 0.7f)
            
            android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 2000
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        start()
                    }
                })
                start()
            }
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        contentLayout?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        
        // Visual feedback for toggle
        if (isExpanded) {
            cosmicWave?.alpha = 0.9f
        } else {
            cosmicWave?.alpha = 0.7f
        }
    }

    private fun updateOverlayForState(state: AgentState) {
        val mascot = toggleButton as? QueMascotView
        when (state) {
            is AgentState.Idle -> {
                statusText?.text = "Idle"
                cosmicWave?.setIntensity(0.1f)
                mascot?.setIsRunning(false)
            }
            is AgentState.Perceiving -> {
                statusText?.text = "Perceiving..."
                cosmicWave?.setIntensity(0.4f)
                mascot?.setIsRunning(true)
            }
            is AgentState.Thinking -> {
                statusText?.text = "Thinking..."
                cosmicWave?.setIntensity(0.7f)
                mascot?.setIsRunning(true)
            }
            is AgentState.Acting -> {
                statusText?.text = "${state.actionDescription}"
                cosmicWave?.setIntensity(1.0f)
                mascot?.setIsRunning(true)
                // Auto-collapse when acting to clear the screen
                if (isExpanded) {
                    toggleExpanded()
                }
            }
            is AgentState.Finished -> {
                statusText?.text = "Finished: ${state.result}"
                cosmicWave?.setIntensity(0.2f)
                mascot?.setIsRunning(false)
            }
            is AgentState.Error -> {
                statusText?.text = "Error: ${state.message}"
                cosmicWave?.setIntensity(0.0f)
                mascot?.setIsRunning(false)
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

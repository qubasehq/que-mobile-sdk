package com.que.platform.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.ContextCompat

class FloatingQueButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null

    companion object {
        private const val TAG = "FloatingQueButton"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "Floating Que Button Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Floating Que Button Service starting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating button: 'Draw over other apps' permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showFloatingButton()
            if (floatingButton == null) {
                Log.w(TAG, "Failed to show floating button, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating button", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun showFloatingButton() {
        if (floatingButton != null) {
            Log.d(TAG, "Floating button already showing")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            // Create the button programmatically
            floatingButton = createFloatingView()
            val button = floatingButton as Button

            // Set up the button click listener
            button.setOnClickListener {
                Log.d(TAG, "Floating Que button clicked!")
                triggerAgentActivation()
            }

            val displayMetrics = resources.displayMetrics
            val margin = (16 * displayMetrics.density).toInt() // 16dp margin

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = margin
                y = margin
            }

            windowManager?.addView(floatingButton, params)
            Log.d(TAG, "Floating Que button added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button", e)
            floatingButton = null
        }
    }

    private fun createFloatingView(): Button {
        return Button(this).apply {
            text = "Hey Que"
            // Prevent text from being all caps for a softer look
            isAllCaps = false
            setTextColor(Color.WHITE)
            // Use the background drawable we created
            background = ContextCompat.getDrawable(context, com.que.platform.android.R.drawable.bg_floating_button)

            // Add elevation for a floating shadow effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8f * resources.displayMetrics.density
                stateListAnimator = null
            }
        }
    }


    private fun triggerAgentActivation() {
        try {
            // For now, we'll try to start a Conversational service (needs to be created)
            // or fallback to the main Agent service if applicable.
            // Placeholder: Log for now, will link to QueConversationalService shortly.
            Log.d(TAG, "Triggering agent activation...")
            val serviceIntent = Intent(this, QueConversationalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting agent service", e)
        }
    }

    private fun hideFloatingButton() {
        floatingButton?.let { button ->
            try {
                if (button.isAttachedToWindow) {
                    windowManager?.removeView(button)
                } else {
                    // No-op
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button", e)
            }
        }
        floatingButton = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Floating Que Button Service destroying...")
        hideFloatingButton()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

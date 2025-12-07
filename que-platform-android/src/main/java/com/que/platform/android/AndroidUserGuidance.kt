package com.que.platform.android

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.que.core.UserGuidance
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Android implementation of UserGuidance that draws an overlay on the screen.
 */
class AndroidUserGuidance(
    private val context: Context,
    private val windowManager: WindowManager
) : UserGuidance {

    private var overlayView: View? = null
    private var statusText: TextView? = null
    private var thoughtText: TextView? = null
    private val scope = MainScope()

    init {
        scope.launch {
            createOverlay()
        }
    }

    private fun createOverlay() {
        if (overlayView != null) return

        try {
            // Simple layout creation programmatically to avoid XML dependency issues in this context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xCC000000.toInt()) // Semi-transparent black
                setPadding(20, 20, 20, 20)
                
                statusText = TextView(context).apply {
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    text = "Que Agent: Idle"
                }
                addView(statusText)
                
                thoughtText = TextView(context).apply {
                    setTextColor(0xFFAAAAAA.toInt())
                    textSize = 12f
                    setPadding(0, 10, 0, 0)
                    visibility = View.GONE
                }
                addView(thoughtText)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP
            
            windowManager.addView(layout, params)
            overlayView = layout
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun showProgress(step: Int, total: Int, description: String) {
        scope.launch {
            statusText?.text = "Step $step/$total: $description"
            thoughtText?.visibility = View.GONE
        }
    }

    override fun showDecision(thought: String, confidence: Float) {
        scope.launch {
            thoughtText?.text = "Thinking (${(confidence * 100).toInt()}%):\n$thought"
            thoughtText?.visibility = View.VISIBLE
        }
    }

    override fun showAlternatives(alternatives: List<String>) {
        // Not implemented for simple overlay
    }

    override suspend fun askForHelp(question: String, options: List<String>): String {
        // Not implemented for simple overlay (requires touchable)
        return ""
    }
    
    fun destroy() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}

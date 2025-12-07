package com.que.platform.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import com.que.core.InteractiveElement

/**
 * Manages a system overlay to draw debug rectangles around perceived elements.
 */
class DebugOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: DebugView? = null
    private var isEnabled = false

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (enabled) {
            addOverlay()
        } else {
            removeOverlay()
        }
    }

    fun updateElements(elements: List<InteractiveElement>) {
        if (!isEnabled) return
        overlayView?.setElements(elements)
    }

    private fun addOverlay() {
        if (overlayView != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // or TYPE_APPLICATION_OVERLAY
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = DebugView(context)
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            // Permission might be missing or other error
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private class DebugView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val textPaint = Paint().apply {
            color = Color.RED
            textSize = 40f
            style = Paint.Style.FILL
        }
        
        private var elements: List<InteractiveElement> = emptyList()

        fun setElements(newElements: List<InteractiveElement>) {
            elements = newElements
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            elements.forEach { element ->
                val r = element.bounds
                canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)
                canvas.drawText(element.id.toString(), r.left.toFloat(), r.top.toFloat(), textPaint)
            }
        }
    }
}

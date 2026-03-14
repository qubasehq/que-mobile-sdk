package com.que.platform.android.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/**
 * Custom View that draws and animates the Que Mascot.
 * Features:
 * - Rounded green background with pulsing glow.
 * - Black face circle.
 * - Blinking white eyes.
 * - Diagonal Q-tail.
 */
class QueMascotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#41D149")
        style = Paint.Style.FILL
    }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private var eyeScaleY = 1.0f
    private var eyeOffsetX = 0f
    private var bgAlpha = 1.0f
    private var isRunning = false
    
    private val blinkIntervalIdle = 4000L
    private val blinkIntervalRunning = 1500L
    private val blinkDuration = 150L

    private var bgAnimator: ValueAnimator? = null
    private var eyeLookAnimator: ValueAnimator? = null

    init {
        startAnimations()
    }

    /**
     * Update the mascot style based on agent activity.
     */
    fun setIsRunning(running: Boolean) {
        if (this.isRunning == running) return
        this.isRunning = running
        
        // Update background pulse
        bgAnimator?.cancel()
        val duration = if (isRunning) 600L else 2000L
        val minAlpha = if (isRunning) 0.8f else 0.5f
        
        bgAnimator = ValueAnimator.ofFloat(minAlpha, 1.0f).apply {
            this.duration = duration
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                bgAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }

        // Eye movement (looking around) - Only when running
        eyeLookAnimator?.cancel()
        if (isRunning) {
            eyeLookAnimator = ValueAnimator.ofFloat(-8f, 8f).apply {
                this.duration = 1000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    eyeOffsetX = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            eyeOffsetX = 0f
        }
    }

    private fun startAnimations() {
        // Blinking animation loop
        post(object : Runnable {
            override fun run() {
                blinkEyes()
                val interval = if (isRunning) blinkIntervalRunning else blinkIntervalIdle
                postDelayed(this, interval + Random.nextLong(1000))
            }
        })

        // Initial background pulse
        setIsRunning(false)
    }

    private fun blinkEyes() {
        ValueAnimator.ofFloat(1.0f, 0.0f, 1.0f).apply {
            duration = blinkDuration
            addUpdateListener { animator ->
                eyeScaleY = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val size = Math.min(w, h)
        val scale = size / 150f // SVG was 150x150

        // 1. Draw Green Rounded Background
        bgPaint.alpha = (bgAlpha * 255).toInt()
        val rect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 25f * scale, 25f * scale, bgPaint)

        // 2. Draw Black Face Circle
        canvas.drawCircle(75f * scale, 75f * scale, 40f * scale, facePaint)

        // 3. Draw Eyes with Blinking and Looking effect
        val eyeWidth = 10f * scale
        val eyeHeightCurrent = 10f * scale * eyeScaleY
        val currentOffsetX = eyeOffsetX * scale
        
        // Left Eye
        canvas.drawOval(
            (60f * scale + currentOffsetX) - eyeWidth, (70f * scale) - eyeHeightCurrent,
            (60f * scale + currentOffsetX) + eyeWidth, (70f * scale) + eyeHeightCurrent,
            eyePaint
        )
        
        // Right Eye
        canvas.drawOval(
            (90f * scale + currentOffsetX) - eyeWidth, (70f * scale) - eyeHeightCurrent,
            (90f * scale + currentOffsetX) + eyeWidth, (70f * scale) + eyeHeightCurrent,
            eyePaint
        )

        // 4. Draw Q Tail
        canvas.drawLine(
            95f * scale, 95f * scale,
            120f * scale, 120f * scale,
            tailPaint
        )
    }
}

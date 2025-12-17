package com.que.platform.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A cosmic/ethereal overlay that shows when the Que agent is active.
 * Displays as a system-wide overlay on top of all apps.
 */
class CosmicOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CosmicView? = null
    private var isShowing = false

    fun show() {
        if (isShowing) return
        
        if (!PermissionManager.hasOverlayPermission(context)) {
            android.util.Log.e("CosmicOverlay", "Attempted to show overlay without permission")
            return
        }

        isShowing = true
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        overlayView = CosmicView(context)
        try {
            windowManager.addView(overlayView, params)
            overlayView?.startAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        
        overlayView?.let {
            it.stopAnimation()
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    fun setAgentState(state: AgentVisualState) {
        overlayView?.setState(state)
    }
    
    enum class AgentVisualState {
        IDLE,       // Subtle pulsing
        THINKING,   // Faster animation, more particles
        ACTING,     // Bright flash effect
        SUCCESS,    // Green glow
        ERROR       // Red pulse
    }

    private class CosmicView(context: Context) : View(context) {
        
        private var animationPhase = 0f
        private var animator: ValueAnimator? = null
        private var currentState = AgentVisualState.IDLE
        
        // Particles for cosmic effect
        private val particles = mutableListOf<Particle>()
        private val particleCount = 30
        
        // Border glow paint
        private val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        
        // Gradient shader for cosmic effect
        private var borderShader: Shader? = null
        
        // Corner radius for rounded effect
        private val cornerRadius = 40f
        
        init {
            // Initialize particles
            repeat(particleCount) {
                particles.add(Particle())
            }
        }
        
        data class Particle(
            var x: Float = Random.nextFloat(),
            var y: Float = Random.nextFloat(),
            var speedX: Float = (Random.nextFloat() - 0.5f) * 0.01f,
            var speedY: Float = (Random.nextFloat() - 0.5f) * 0.01f,
            var size: Float = Random.nextFloat() * 8f + 2f,
            var alpha: Float = Random.nextFloat() * 0.5f + 0.3f,
            var hue: Float = Random.nextFloat() * 360f
        )
        
        fun startAnimation() {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 4000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { 
                    animationPhase = it.animatedValue as Float
                    updateParticles()
                    postInvalidate()
                }
                start()
            }
        }
        
        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }
        
        fun setState(state: AgentVisualState) {
            currentState = state
            // Adjust animation speed based on state
            animator?.let {
                it.duration = when (state) {
                    AgentVisualState.IDLE -> 4000
                    AgentVisualState.THINKING -> 1500
                    AgentVisualState.ACTING -> 800
                    AgentVisualState.SUCCESS -> 2000
                    AgentVisualState.ERROR -> 500
                }
            }
        }
        
        private fun updateParticles() {
            particles.forEach { p ->
                p.x += p.speedX
                p.y += p.speedY
                p.hue = (p.hue + 1f) % 360f
                
                // Wrap around edges
                if (p.x < 0) p.x = 1f
                if (p.x > 1) p.x = 0f
                if (p.y < 0) p.y = 1f
                if (p.y > 1) p.y = 0f
            }
        }
        
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            createBorderShader(w, h)
        }
        
        private fun createBorderShader(w: Int, h: Int) {
            val colors = intArrayOf(
                Color.parseColor("#FF6B6B"),  // Coral
                Color.parseColor("#C44569"),  // Pink
                Color.parseColor("#546DE5"),  // Blue
                Color.parseColor("#574B90"),  // Purple
                Color.parseColor("#303952"),  // Dark blue
                Color.parseColor("#FF6B6B")   // Back to coral
            )
            
            borderShader = SweepGradient(
                w / 2f, h / 2f,
                colors,
                null
            )
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val w = width.toFloat()
            val h = height.toFloat()
            
            if (w == 0f || h == 0f) return
            
            // Rotate the gradient based on animation phase
            val matrix = Matrix()
            matrix.setRotate(animationPhase, w / 2f, h / 2f)
            borderShader?.setLocalMatrix(matrix)
            
            // Draw animated border glow
            borderPaint.shader = borderShader
            
            // Calculate glow intensity based on state
            val glowAlpha = when (currentState) {
                AgentVisualState.IDLE -> 80 + (sin(animationPhase * Math.PI / 180.0) * 40).toInt()
                AgentVisualState.THINKING -> 120 + (sin(animationPhase * Math.PI / 90.0) * 60).toInt()
                AgentVisualState.ACTING -> 200
                AgentVisualState.SUCCESS -> 150
                AgentVisualState.ERROR -> 180 + (sin(animationPhase * Math.PI / 45.0) * 75).toInt()
            }
            
            // Draw multiple border layers for glow effect
            for (i in 0..4) {
                borderPaint.strokeWidth = (12 - i * 2).toFloat()
                borderPaint.alpha = (glowAlpha * (1 - i * 0.15)).toInt().coerceIn(0, 255)
                
                val inset = i * 4f + 4f
                val rect = RectF(inset, inset, w - inset, h - inset)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            }
            
            // Draw floating particles
            val particlePaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            particles.forEach { p ->
                val px = p.x * w
                val py = p.y * h
                
                // Only draw particles near the edges (within 50 pixels)
                val edgeDist = minOf(px, py, w - px, h - py)
                if (edgeDist < 60) {
                    val hsv = floatArrayOf(p.hue, 0.8f, 1f)
                    particlePaint.color = Color.HSVToColor((p.alpha * 255 * (1 - edgeDist/60)).toInt(), hsv)
                    canvas.drawCircle(px, py, p.size, particlePaint)
                }
            }
            
            // Draw corner accents
            drawCornerAccent(canvas, 0f, 0f, 1f, 1f)  // Top-left
            drawCornerAccent(canvas, w, 0f, -1f, 1f)  // Top-right
            drawCornerAccent(canvas, 0f, h, 1f, -1f)  // Bottom-left
            drawCornerAccent(canvas, w, h, -1f, -1f)  // Bottom-right
        }
        
        private fun drawCornerAccent(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float) {
            val paint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            val size = 80f
            val pulseSize = size + (sin(animationPhase * Math.PI / 180.0) * 10).toFloat()
            
            val gradient = RadialGradient(
                x + dx * 30, y + dy * 30, pulseSize,
                intArrayOf(
                    Color.argb(100, 150, 100, 255),
                    Color.argb(50, 100, 150, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            
            paint.shader = gradient
            canvas.drawCircle(x + dx * 30, y + dy * 30, pulseSize, paint)
        }
    }
}

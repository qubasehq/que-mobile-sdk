package com.que.platform.android.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cosmic animated wave view with blue and pink gradient colors.
 * Based on Blurr's AudioWaveView but adapted for agent status visualization.
 */
class CosmicWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @Suppress("UNUSED_PARAMETER") defStyleAttr: Int = 0
) : View(context, attrs) {

    private val waveCount = 7
    private val minIdleAmplitude = 0.15f
    private val maxWaveHeightScale = 0.25f
    private val maxSpeedIncrease = 4.0f
    private val jitterAmount = 0.1f

    // Cosmic theme colors - rainbow mix
    private val waveColors = intArrayOf(
        Color.parseColor("#FF0000"), // Red
        Color.parseColor("#FF7F00"), // Orange
        Color.parseColor("#FFFF00"), // Yellow
        Color.parseColor("#00FF00"), // Green
        Color.parseColor("#0000FF"), // Blue
        Color.parseColor("#4B0082"), // Indigo
        Color.parseColor("#9400D3")  // Violet
    )

    private var amplitudeAnimator: ValueAnimator? = null
    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray
    private val waveSpeeds: FloatArray
    private val waveAmplitudeMultipliers: FloatArray

    private var audioAmplitude = minIdleAmplitude
    private var lastTime = System.currentTimeMillis()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        waveFrequencies = FloatArray(waveCount)
        wavePhaseShifts = FloatArray(waveCount)
        waveSpeeds = FloatArray(waveCount)
        waveAmplitudeMultipliers = FloatArray(waveCount)

        val blurFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until waveCount) {
            waveFrequencies[i] = Random.nextFloat() * 0.6f + 0.8f
            wavePhaseShifts[i] = Random.nextFloat() * (Math.PI * 2).toFloat()
            waveSpeeds[i] = Random.nextFloat() * 0.02f + 0.01f
            waveAmplitudeMultipliers[i] = Random.nextFloat() * 0.5f + 0.8f

            wavePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = waveColors[i % waveColors.size]
                alpha = 120
                maskFilter = blurFilter
            })
            wavePaths.add(Path())
        }
    }

    /**
     * Set the wave intensity (0.0 = calm, 1.0 = active)
     */
    fun setIntensity(intensity: Float) {
        val scaledAmplitude = intensity.pow(1.5f).coerceIn(0.0f, 1.0f)
        val targetAmplitude = minIdleAmplitude + (scaledAmplitude * maxWaveHeightScale)
        
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = 300L
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        for (i in 0 until waveCount) {
            val paint = wavePaints[i]
            val color = waveColors[i % waveColors.size]
            paint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastTime)
        lastTime = currentTime
        
        val speedFactor = 1.0f + (audioAmplitude * maxSpeedIncrease)

        for (i in 0 until waveCount) {
            // Update phase shift smoothly over time
            wavePhaseShifts[i] += (waveSpeeds[i] * speedFactor * deltaTime / 16f)
            
            wavePaths[i].reset()
            // Draw from TOP down
            wavePaths[i].moveTo(0f, 0f)
            val waveMaxHeight = height * audioAmplitude * waveAmplitudeMultipliers[i]
            val currentJitter = (Random.nextFloat() - 0.5f) * waveMaxHeight * jitterAmount
            
            // Step 10px instead of 5px for better performance since it's smoothing now
            for (x in 0..width step 10) {
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + wavePhaseShifts[i]
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)
                val y = (waveMaxHeight * sineOutput) + currentJitter
                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }
            wavePaths[i].lineTo(width.toFloat(), 0f)
            wavePaths[i].close()
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
        
        // Loop animation!
        postInvalidateOnAnimation()
    }
}

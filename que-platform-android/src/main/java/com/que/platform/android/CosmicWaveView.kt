package com.que.platform.android

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

    // Cosmic theme colors - blue and pink mix
    private val waveColors = intArrayOf(
        "#8A2BE2".toColorInt(), // Blue Violet
        "#4169E1".toColorInt(), // Royal Blue
        "#FF1493".toColorInt(), // Deep Pink
        "#9370DB".toColorInt(), // Medium Purple
        "#00BFFF".toColorInt(), // Deep Sky Blue
        "#FF69B4".toColorInt(), // Hot Pink
        "#DA70D6".toColorInt()  // Orchid
    )

    private var amplitudeAnimator: ValueAnimator? = null
    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray
    private val waveSpeeds: FloatArray
    private val waveAmplitudeMultipliers: FloatArray

    private var audioAmplitude = minIdleAmplitude

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

        // Animation loop
        ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val speedFactor = 1.0f + (audioAmplitude * maxSpeedIncrease)
                for (i in 0 until waveCount) {
                    wavePhaseShifts[i] += (waveSpeeds[i] * speedFactor)
                }
                invalidate()
            }
            start()
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
                0f, h / 2f, 0f, h.toFloat(),
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until waveCount) {
            wavePaths[i].reset()
            wavePaths[i].moveTo(0f, height.toFloat())
            val waveMaxHeight = height * audioAmplitude * waveAmplitudeMultipliers[i]
            val currentJitter = (Random.nextFloat() - 0.5f) * waveMaxHeight * jitterAmount
            for (x in 0..width step 5) {
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + wavePhaseShifts[i]
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)
                val y = height - (waveMaxHeight * sineOutput) + currentJitter
                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }
            wavePaths[i].lineTo(width.toFloat(), height.toFloat())
            wavePaths[i].close()
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
    }
}

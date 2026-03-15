package com.que.platform.android.service
import com.que.core.model.AgentState
import com.que.core.service.Agent
import com.que.platform.android.overlay.CosmicWaveView
import com.que.platform.android.overlay.QueMascotView
import com.que.platform.android.util.STTManager

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Floating overlay service with premium UI for agent status, live logs,
 * interactive question/confirmation panels, and narration banners.
 * Sits on top of all apps. Tapping the mascot expands/collapses the panel.
 */
class CosmicOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var cosmicWave: CosmicWaveView? = null
    private var statusText: TextView? = null
    private var narrationBanner: TextView? = null
    private var logsContainer: LinearLayout? = null
    private var logsScrollView: ScrollView? = null
    private var contentLayout: View? = null
    private var interactionPanel: LinearLayout? = null
    private var toggleButton: View? = null
    private var isExpanded = false
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var logCount = 0
    private val MAX_VISIBLE_LOGS = 80
    
    // Voice / STT
    private var sttJob: Job? = null
    private val sttManager by lazy { STTManager(this) }
    
    // Colors for the premium dark theme
    private val COL_BG = Color.parseColor("#F0101018")
    private val COL_SURFACE = Color.parseColor("#FF1A1A2E")
    private val COL_ACCENT = Color.parseColor("#FF6366F1")  // indigo
    private val COL_CYAN = Color.parseColor("#FF22D3EE")
    private val COL_GREEN = Color.parseColor("#FF10B981")
    private val COL_YELLOW = Color.parseColor("#FFF59E0B")
    private val COL_ORANGE = Color.parseColor("#FFF97316")
    private val COL_RED = Color.parseColor("#FFEF4444")
    private val COL_TEXT = Color.parseColor("#FFF1F5F9")
    private val COL_TEXT_DIM = Color.parseColor("#FF94A3B8")
    private val COL_BORDER = Color.parseColor("#FF334155")

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

        /**
         * Show an interactive question panel inside the overlay.
         * The user can tap quick-reply options or type a custom reply.
         */
        fun showQuestion(question: String, options: List<String>?, onReply: (String) -> Unit) {
            instance?.showInteractionPanel(
                icon = "🙋",
                title = "Agent Question",
                body = question,
                options = options,
                confirmMode = false,
                onReply = onReply
            )
        }

        /**
         * Show a confirmation panel inside the overlay.
         * The user can tap Approve or Deny.
         */
        fun showConfirmation(summary: String, actionPreview: String, onReply: (String) -> Unit) {
            instance?.showInteractionPanel(
                icon = "⚠️",
                title = "Confirm Action",
                body = "$summary\n\n$actionPreview",
                options = listOf("✅ Approve", "❌ Deny"),
                confirmMode = true,
                onReply = onReply
            )
        }

        /**
         * Show a brief narration banner below the cosmic wave.
         */
        fun showNarration(message: String, type: String) {
            instance?.displayNarration(message, type)
        }

        /**
         * Dismiss any active interaction panel.
         */
        fun dismissInteraction() {
            instance?.hideInteractionPanel()
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "CosmicOverlayService created")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Cosmic Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Que Agent Active")
            .setContentText("Cosmic overlay is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        createOverlay()
        
        scope.launch {
            agentState
                .debounce(100)
                .collect { state -> updateOverlayForState(state) }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        overlayView = createOverlayLayout()
        
        try {
            windowManager?.addView(overlayView, overlayParams)
            Log.d(TAG, "Overlay added to window")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun createOverlayLayout(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }

        // ── Cosmic Wave Glow (top strip) ──
        cosmicWave = CosmicWaveView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { gravity = Gravity.TOP }
            alpha = 0.7f
        }
        root.addView(cosmicWave)

        // ── Narration Banner (just below glow, auto-hides) ──
        narrationBanner = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(50)
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1A2E"))
                cornerRadius = dp(8).toFloat()
                setStroke(1, COL_CYAN)
            }
            background = bg
            setPadding(dp(12), dp(6), dp(12), dp(6))
            textSize = 12f
            setTextColor(COL_CYAN)
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            maxLines = 2
        }
        root.addView(narrationBanner)

        // ── Mascot Toggle Button ──
        toggleButton = QueMascotView(this).apply {
            val size = dp(36)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            }
            setOnClickListener { toggleExpanded() }
        }
        root.addView(toggleButton)

        // ── Expandable Content Panel ──
        contentLayout = buildContentPanel()
        root.addView(contentLayout)

        return root
    }

    private fun buildContentPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(56)
            }
            val bg = GradientDrawable().apply {
                setColor(COL_BG)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
            }
            background = bg
            setPadding(dp(16), dp(12), dp(16), dp(16))
            visibility = View.GONE
        }

        // ── Status Header ──
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerLabel = TextView(this).apply {
            text = "QUE AGENT"
            textSize = 10f
            setTextColor(COL_TEXT_DIM)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(headerLabel)

        statusText = TextView(this).apply {
            text = "● Idle"
            textSize = 12f
            setTextColor(COL_GREEN)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            }
        }
        headerRow.addView(statusText)

        // Voice Toggle button
        val voiceToggle = TextView(this).apply {
            text = if (QueAgentService.isVoiceEnabled) "🔊 ON" else "🔇 OFF"
            textSize = 10f
            setTextColor(if (QueAgentService.isVoiceEnabled) COL_CYAN else COL_TEXT_DIM)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FF252545"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            setOnClickListener {
                QueAgentService.isVoiceEnabled = !QueAgentService.isVoiceEnabled
                text = if (QueAgentService.isVoiceEnabled) "🔊 ON" else "🔇 OFF"
                setTextColor(if (QueAgentService.isVoiceEnabled) COL_CYAN else COL_TEXT_DIM)
            }
        }
        headerRow.addView(voiceToggle)
        panel.addView(headerRow)

        // ── Divider ──
        panel.addView(makeDivider())

        // ── Interaction Panel (questions/confirmations — hidden by default) ──
        interactionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            visibility = View.GONE
        }
        panel.addView(interactionPanel)

        // ── Logs Section ──
        val logsLabel = TextView(this).apply {
            text = "LIVE ACTIVITY"
            textSize = 9f
            setTextColor(COL_TEXT_DIM)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(4) }
        }
        panel.addView(logsLabel)

        logsScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(250)
            )
            isVerticalScrollBarEnabled = true
        }

        logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        logsScrollView!!.addView(logsContainer)
        panel.addView(logsScrollView)

        return panel
    }

    private fun makeDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = dp(8); bottomMargin = dp(4) }
            setBackgroundColor(COL_BORDER)
        }
    }

    // ════════════════════════════════════════════════
    // Interactive Panel (Questions / Confirmations)
    // ════════════════════════════════════════════════

    private fun showInteractionPanel(
        icon: String,
        title: String,
        body: String,
        options: List<String>?,
        confirmMode: Boolean,
        onReply: (String) -> Unit
    ) {
        scope.launch(Dispatchers.Main) {
            interactionPanel?.let { panel ->
                panel.removeAllViews()

                // Panel background with accent border
                val accentColor = if (confirmMode) COL_ORANGE else COL_YELLOW
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF16162A"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), accentColor)
                }
                panel.background = bg
                panel.setPadding(dp(14), dp(12), dp(14), dp(14))

                // Icon + Title row
                val titleRow = LinearLayout(this@CosmicOverlayService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                titleRow.addView(TextView(this@CosmicOverlayService).apply {
                    text = icon
                    textSize = 18f
                    setPadding(0, 0, dp(8), 0)
                })
                titleRow.addView(TextView(this@CosmicOverlayService).apply {
                    text = title
                    textSize = 14f
                    setTextColor(accentColor)
                    typeface = Typeface.DEFAULT_BOLD
                })
                panel.addView(titleRow)

                // Body text
                val bodyView = TextView(this@CosmicOverlayService).apply {
                    text = body
                    textSize = 13f
                    setTextColor(COL_TEXT)
                    setLineSpacing(4f, 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                }
                panel.addView(bodyView)

                // Options as pill buttons (scrollable horizontally)
                if (!options.isNullOrEmpty()) {
                    val scrollContainer = HorizontalScrollView(this@CosmicOverlayService).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(10) }
                        isHorizontalScrollBarEnabled = false
                    }

                    val pillRow = LinearLayout(this@CosmicOverlayService).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    for (option in options) {
                        val pill = TextView(this@CosmicOverlayService).apply {
                            text = option
                            textSize = 12f
                            setTextColor(if (confirmMode && option.contains("Deny")) COL_RED else COL_TEXT)
                            typeface = Typeface.DEFAULT_BOLD

                            val pillBg = GradientDrawable().apply {
                                setColor(if (confirmMode && option.contains("Approve")) Color.parseColor("#FF1B3A2F")
                                         else if (confirmMode && option.contains("Deny")) Color.parseColor("#FF3A1B1B")
                                         else Color.parseColor("#FF252545"))
                                cornerRadius = dp(20).toFloat()
                                setStroke(1, if (confirmMode && option.contains("Approve")) COL_GREEN
                                             else if (confirmMode && option.contains("Deny")) COL_RED
                                             else COL_ACCENT)
                            }
                            background = pillBg
                            setPadding(dp(16), dp(8), dp(16), dp(8))

                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginEnd = dp(8) }
                            layoutParams = lp

                            setOnClickListener {
                                val reply = if (confirmMode) {
                                    if (option.contains("Approve")) "yes" else "no"
                                } else option
                                onReply(reply)
                                hideInteractionPanel()
                            }
                        }
                        pillRow.addView(pill)
                    }

                    scrollContainer.addView(pillRow)
                    panel.addView(scrollContainer)
                }

                // Custom reply text input (for questions, not confirmations)
                if (!confirmMode) {
                    val inputRow = LinearLayout(this@CosmicOverlayService).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(10) }
                    }

                    val inputBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#FF252545"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(1, COL_BORDER)
                    }
                    val replyInput = EditText(this@CosmicOverlayService).apply {
                        hint = "Type your reply..."
                        setHintTextColor(COL_TEXT_DIM)
                        textSize = 13f
                        setTextColor(COL_TEXT)
                        background = inputBg
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        isSingleLine = true
                        layoutParams = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                        ).apply { marginEnd = dp(8) }
                    }
                    inputRow.addView(replyInput)

                    val sendBg = GradientDrawable().apply {
                        setColor(COL_ACCENT)
                        cornerRadius = dp(10).toFloat()
                    }
                    val sendBtn = TextView(this@CosmicOverlayService).apply {
                        text = "Send"
                        textSize = 12f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        background = sendBg
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                        gravity = Gravity.CENTER
                        setOnClickListener {
                            val text = replyInput.text.toString().trim()
                            if (text.isNotEmpty()) {
                                onReply(text)
                                hideInteractionPanel()
                            }
                        }
                    }
                    inputRow.addView(sendBtn)
                    panel.addView(inputRow)

                    // Make overlay focusable so keyboard works
                    enableFocusableMode()
                }

                // --- Auto-Voice Input (STT) ---
                if (QueAgentService.isVoiceEnabled) {
                    val sttStatus = TextView(this@CosmicOverlayService).apply {
                        text = "🎙️ Listening for your voice..."
                        textSize = 11f
                        setTextColor(COL_GREEN)
                        setPadding(0, dp(12), 0, dp(4))
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    panel.addView(sttStatus)
                    
                    sttJob?.cancel()
                    sttJob = scope.launch(Dispatchers.Main) {
                        try {
                            sttManager.startListening().collect { transcript ->
                                sttStatus.text = "🗣️ \"$transcript\""
                                onReply(transcript)
                                hideInteractionPanel()
                                sttJob?.cancel()
                            }
                        } catch (e: Exception) {
                            sttStatus.text = "Microphone ended. Please type."
                            sttStatus.setTextColor(COL_TEXT_DIM)
                        }
                    }
                }

                panel.visibility = View.VISIBLE
                // Auto-expand panel if collapsed
                if (!isExpanded) toggleExpanded()

                appendLog("[${if (confirmMode) "CONFIRM" else "QUESTION"}] $body")
            }
        }
    }

    private fun hideInteractionPanel() {
        scope.launch(Dispatchers.Main) {
            sttJob?.cancel()
            interactionPanel?.visibility = View.GONE
            interactionPanel?.removeAllViews()
            disableFocusableMode()
        }
    }

    private fun enableFocusableMode() {
        overlayParams?.let { params ->
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) {}
        }
    }

    private fun disableFocusableMode() {
        overlayParams?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) {}
        }
    }

    // ════════════════════════════════════════════════
    // Narration Banner
    // ════════════════════════════════════════════════

    private var narrationHideJob: Job? = null

    private fun displayNarration(message: String, type: String) {
        scope.launch(Dispatchers.Main) {
            val color = when (type) {
                "found" -> COL_GREEN
                "warning" -> COL_YELLOW
                "done" -> COL_ACCENT
                else -> COL_CYAN  // progress
            }

            val emoji = when (type) {
                "found" -> "🔍"
                "warning" -> "⚠️"
                "done" -> "✅"
                else -> "💬"
            }

            narrationBanner?.let { banner ->
                banner.text = "$emoji $message"
                banner.setTextColor(color)
                val bg = banner.background as? GradientDrawable
                bg?.setStroke(1, color)
                banner.visibility = View.VISIBLE
                banner.alpha = 0f
                banner.animate().alpha(1f).setDuration(200).start()

                // Auto-hide after 4 seconds
                narrationHideJob?.cancel()
                narrationHideJob = scope.launch {
                    kotlinx.coroutines.delay(4000)
                    banner.animate().alpha(0f).setDuration(300).withEndAction {
                        banner.visibility = View.GONE
                    }.start()
                }
            }

            appendLog("[NARRATE:$type] $message")
        }
    }

    // ════════════════════════════════════════════════
    // Toggle / State
    // ════════════════════════════════════════════════

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        contentLayout?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        cosmicWave?.alpha = if (isExpanded) 0.9f else 0.7f
    }

    private fun updateOverlayForState(state: AgentState) {
        val mascot = toggleButton as? QueMascotView
        when (state) {
            is AgentState.Idle -> {
                statusText?.text = "● Idle"
                statusText?.setTextColor(COL_TEXT_DIM)
                cosmicWave?.setIntensity(0.1f)
                mascot?.setVisualState(QueMascotView.MascotState.IDLE)
            }
            is AgentState.Perceiving -> {
                statusText?.text = "👁 Perceiving..."
                statusText?.setTextColor(COL_CYAN)
                cosmicWave?.setIntensity(0.4f)
                mascot?.setVisualState(QueMascotView.MascotState.THINKING)
            }
            is AgentState.Thinking -> {
                statusText?.text = "🧠 Thinking..."
                statusText?.setTextColor(COL_ACCENT)
                cosmicWave?.setIntensity(0.7f)
                mascot?.setVisualState(QueMascotView.MascotState.THINKING)
            }
            is AgentState.Acting -> {
                statusText?.text = "⚡ ${state.actionDescription}"
                statusText?.setTextColor(COL_GREEN)
                cosmicWave?.setIntensity(1.0f)
                mascot?.setVisualState(QueMascotView.MascotState.ACTING)
                if (isExpanded && interactionPanel?.visibility != View.VISIBLE) {
                    toggleExpanded()
                }
            }
            is AgentState.WaitingForUser -> {
                statusText?.text = "🙋 Waiting for you..."
                statusText?.setTextColor(COL_YELLOW)
                cosmicWave?.setIntensity(0.5f)
                mascot?.setVisualState(QueMascotView.MascotState.IDLE)
            }
            is AgentState.Finished -> {
                statusText?.text = "✅ Done: ${state.result.take(40)}"
                statusText?.setTextColor(COL_GREEN)
                cosmicWave?.setIntensity(0.2f)
                mascot?.setVisualState(QueMascotView.MascotState.IDLE)
            }
            is AgentState.Error -> {
                statusText?.text = "❌ ${state.message.take(40)}"
                statusText?.setTextColor(COL_RED)
                cosmicWave?.setIntensity(0.0f)
                mascot?.setVisualState(QueMascotView.MascotState.IDLE)
            }
        }
    }

    // ════════════════════════════════════════════════
    // Rich Log Entries
    // ════════════════════════════════════════════════

    private fun appendLog(message: String) {
        scope.launch(Dispatchers.Main) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

            // Determine log type / color from tag
            val (emoji, color) = when {
                message.startsWith("[PERCEIVING]") -> "👁" to COL_CYAN
                message.startsWith("[THINKING]")   -> "🧠" to COL_ACCENT
                message.startsWith("[ACTING]")     -> "⚡" to COL_GREEN
                message.startsWith("[WAITING]")    -> "🙋" to COL_YELLOW
                message.startsWith("[QUESTION]")   -> "❓" to COL_YELLOW
                message.startsWith("[CONFIRM]")    -> "⚠️" to COL_ORANGE
                message.startsWith("[NARRATE")     -> "💬" to COL_CYAN
                message.startsWith("[USER REPLY]") -> "👤" to COL_GREEN
                message.startsWith("[FINISHED]")   -> "✅" to COL_GREEN
                message.startsWith("[ERROR]")      -> "❌" to COL_RED
                message.startsWith("[PLAN]")       -> "📋" to COL_ACCENT
                else                               -> "•" to COL_TEXT_DIM
            }

            logsContainer?.let { container ->
                // Create a rich log entry view
                val entry = LinearLayout(this@CosmicOverlayService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(2) }
                    gravity = Gravity.TOP
                }

                // Timestamp
                entry.addView(TextView(this@CosmicOverlayService).apply {
                    text = timestamp
                    textSize = 9f
                    setTextColor(COL_TEXT_DIM)
                    typeface = Typeface.MONOSPACE
                    setPadding(0, dp(1), dp(6), 0)
                })

                // Emoji indicator
                entry.addView(TextView(this@CosmicOverlayService).apply {
                    text = emoji
                    textSize = 10f
                    setPadding(0, 0, dp(4), 0)
                })

                // Message text (colored)
                entry.addView(TextView(this@CosmicOverlayService).apply {
                    text = message
                    textSize = 11f
                    setTextColor(color)
                    maxLines = 3
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })

                container.addView(entry)
                logCount++

                // Trim old entries to keep memory bounded
                if (logCount > MAX_VISIBLE_LOGS) {
                    container.removeViewAt(0)
                    logCount--
                }

                // Auto-scroll to bottom
                logsScrollView?.post {
                    logsScrollView?.fullScroll(View.FOCUS_DOWN)
                }
            }
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

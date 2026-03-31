package com.que.platform.android.service

import com.que.core.assistant.AssistantEvent
import com.que.core.assistant.AssistantMode
import com.que.core.assistant.AssistantSettings
import com.que.core.assistant.QueAssistant
import com.que.core.engine.QueAgent
import com.que.core.model.AgentSettings
import com.que.core.service.SpeechService
import com.que.core.util.AgentLogger
import com.que.platform.android.engine.AgentNotificationManager
import com.que.platform.android.engine.AndroidFileSystem
import com.que.platform.android.engine.QuePerceptionEngine
import com.que.platform.android.engine.SpeechCoordinator
import com.que.platform.android.util.AndroidEventMonitor
import com.que.platform.android.util.AndroidIntentRegistry
import com.que.platform.android.util.AppLauncher
import com.que.actions.AndroidActionExecutor
import com.que.llm.GeminiClient
import com.que.platform.android.voice.PorcupineWakeWordDetector

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Callback for QueAssistant events to Expo bridge.
 */
typealias AssistantEventListener = (eventType: String, data: Map<String, Any?>) -> Unit

/**
 * Foreground Service hosting QueAssistant.
 * Mirrors the QueAgentService pattern but for CHAT / ASSISTANT modes.
 *
 * Lifecycle:
 *   1. Expo bridge calls start() → service starts, creates QueAssistant instance
 *   2. Expo bridge calls send() → forwards text to assistant.send()
 *   3. Expo bridge calls replyToAssistant() → forwards to assistant.replyToAgent()
 *   4. Expo bridge calls stop() → stops assistant and service
 */
class QueAssistantService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var assistant: QueAssistant? = null
    private lateinit var speechCoordinator: SpeechCoordinator
    private lateinit var notificationManager: AgentNotificationManager
    private var wakeWordDetector: PorcupineWakeWordDetector? = null

    companion object {
        private const val TAG = "QueAssistantService"
        private const val EXTRA_MODE = "com.que.EXTRA_MODE"
        private const val EXTRA_MODEL = "com.que.EXTRA_MODEL"
        private const val ACTION_STOP = "com.que.ACTION_STOP_ASSISTANT"
        private const val ACTION_SEND = "com.que.ACTION_SEND_MESSAGE"
        private const val ACTION_REPLY = "com.que.ACTION_REPLY_TO_AGENT"
        private const val ACTION_CLEAR = "com.que.ACTION_CLEAR_MEMORY"
        private const val ACTION_START_WAKEWORD = "com.que.ACTION_START_WAKEWORD"
        private const val ACTION_STOP_WAKEWORD = "com.que.ACTION_STOP_WAKEWORD"
        private const val EXTRA_WAKEWORD_KEY = "com.que.EXTRA_WAKEWORD_KEY"
        private const val EXTRA_TEXT = "com.que.EXTRA_TEXT"
        private const val EXTRA_VOICE = "com.que.EXTRA_VOICE"

        @Volatile var isRunning: Boolean = false; private set
        @Volatile var currentMode: String = "ASSISTANT"; private set

        // Session API key — set before start()
        private var sessionApiKey: String? = null

        // Listeners for Expo bridge
        private var eventListener: AssistantEventListener? = null

        // Weak ref to active assistant for static calls
        private var activeRef: WeakReference<QueAssistant>? = null

        fun setApiKey(key: String) { sessionApiKey = key }
        fun setAssistantEventListener(listener: AssistantEventListener?) { eventListener = listener }

        /**
         * Start the assistant service with a mode.
         */
        fun start(context: Context, mode: String = "ASSISTANT", model: String = "gemini-2.5-flash", voiceName: String? = null) {
            AgentLogger.d(TAG, "Starting assistant service — mode: $mode, model: $model, voice: $voiceName")
            val intent = Intent(context, QueAssistantService::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_MODEL, model)
                if (voiceName != null) putExtra(EXTRA_VOICE, voiceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Send a message to the running assistant.
         */
        fun send(text: String) {
            val a = activeRef?.get()
            if (a != null) {
                AgentLogger.d(TAG, "Sending to assistant: $text")
                a.send(text)
            } else {
                AgentLogger.e(TAG, "Cannot send: no active assistant")
            }
        }

        /**
         * Reply to an agent question or confirmation within the assistant.
         */
        fun replyToAssistant(reply: String) {
            val a = activeRef?.get()
            if (a != null) {
                AgentLogger.d(TAG, "Replying to assistant agent: $reply")
                a.replyToAgent(reply)
            } else {
                AgentLogger.e(TAG, "Cannot reply: no active assistant")
            }
        }

        /**
         * Clear assistant conversation memory.
         */
        fun clearMemory() {
            activeRef?.get()?.clearMemory()
        }

        fun startWakeWord(context: Context, accessKey: String) {
            Log.d(TAG, ">>> Companion.startWakeWord: key length=${accessKey.length}, blank=${accessKey.isBlank()}")
            val intent = Intent(context, QueAssistantService::class.java).apply {
                action = ACTION_START_WAKEWORD
                putExtra(EXTRA_WAKEWORD_KEY, accessKey)
            }
            Log.d(TAG, ">>> Dispatching ACTION_START_WAKEWORD intent to service")
            context.startService(intent)
        }

        fun stopWakeWord(context: Context) {
            AgentLogger.d(TAG, "Dispatching Stop Wake Word")
            val intent = Intent(context, QueAssistantService::class.java).apply {
                action = ACTION_STOP_WAKEWORD
            }
            context.startService(intent)
        }

        /**
         * Stop the assistant service.
         */
        fun stop(context: Context) {
            AgentLogger.d(TAG, "Stopping assistant service")
            val intent = Intent(context, QueAssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        notificationManager = AgentNotificationManager(this)
        speechCoordinator = SpeechCoordinator.getInstance(this)
        setupWakeWord()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy foreground requirement immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
            startForeground(
                AgentNotificationManager.NOTIFICATION_ID + 1,
                notificationManager.buildForegroundNotification("QUE Assistant active"),
                serviceType
            )
        } else {
            startForeground(
                AgentNotificationManager.NOTIFICATION_ID + 1,
                notificationManager.buildForegroundNotification("QUE Assistant active")
            )
        }

        // First: ensure we have an API key (session or persisted)
        if (sessionApiKey == null) {
            val prefs = getSharedPreferences("QuePrefs", Context.MODE_PRIVATE)
            sessionApiKey = prefs.getString("API_KEY", null)
            if (sessionApiKey != null) {
                Log.d(TAG, "Restored Gemini API Key from prefs")
            }
        }

        // Second: Initialize Assistant if missing
        if (assistant == null && sessionApiKey != null) {
            val modeStr = intent?.getStringExtra(EXTRA_MODE) ?: "ASSISTANT"
            val model = intent?.getStringExtra(EXTRA_MODEL) ?: "gemini-2.5-flash"
            val voiceName = intent?.getStringExtra(EXTRA_VOICE)
            currentMode = modeStr
            
            val tempKey = sessionApiKey!!
            // We DON'T clear sessionApiKey here if we want it to survive for QueAgentService later
            // But initializeAssistant takes it. Let's just pass it.
            initializeAssistant(tempKey, modeStr, model, voiceName)
        } else if (assistant == null && sessionApiKey == null) {
             Log.w(TAG, "No API key available for assistant initialization (yet)")
        }

        // Third: Process actions
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received")
                shutdownAssistant()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SEND -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_STICKY
                if (assistant != null) {
                    send(text)
                } else {
                    Log.e(TAG, "Cannot send: assistant not initialized")
                }
                return START_STICKY
            }
            ACTION_REPLY -> {
                val reply = intent.getStringExtra(EXTRA_TEXT) ?: return START_STICKY
                replyToAssistant(reply)
                return START_STICKY
            }
            ACTION_CLEAR -> {
                clearMemory()
                return START_STICKY
            }
            ACTION_START_WAKEWORD -> {
                Log.d(TAG, ">>> ACTION_START_WAKEWORD received")
                val accessKeyFromIntent = intent.getStringExtra(EXTRA_WAKEWORD_KEY)
                Log.d(TAG, ">>> Intent key: length=${accessKeyFromIntent?.length ?: -1}, blank=${accessKeyFromIntent.isNullOrBlank()}")
                
                val prefs = getSharedPreferences("QuePrefs", Context.MODE_PRIVATE)
                val prefsKey = prefs.getString("PICOVOICE_KEY", "") ?: ""
                Log.d(TAG, ">>> Prefs key: length=${prefsKey.length}, blank=${prefsKey.isBlank()}")
                
                val buildConfigKey = com.que.platform.android.BuildConfig.PORCUPINE_ACCESS_KEY
                Log.d(TAG, ">>> BuildConfig key: length=${buildConfigKey.length}, blank=${buildConfigKey.isBlank()}")
                
                val finalAccessKey = when {
                    !accessKeyFromIntent.isNullOrBlank() -> {
                        Log.d(TAG, ">>> Using key from INTENT")
                        accessKeyFromIntent
                    }
                    prefsKey.isNotBlank() -> {
                        Log.d(TAG, ">>> Using key from PREFS")
                        prefsKey
                    }
                    buildConfigKey.isNotBlank() -> {
                        Log.d(TAG, ">>> Using key from BUILD_CONFIG")
                        buildConfigKey
                    }
                    else -> {
                        Log.e(TAG, ">>> ALL KEY SOURCES ARE EMPTY! Wake word cannot start.")
                        ""
                    }
                }
                
                if (finalAccessKey.isNotBlank()) {
                    Log.d(TAG, ">>> Starting Porcupine with key: ${finalAccessKey.take(4)}...${finalAccessKey.takeLast(4)} (len=${finalAccessKey.length})")
                    if (wakeWordDetector == null) {
                        Log.e(TAG, ">>> wakeWordDetector is NULL! Cannot start.")
                    } else {
                        wakeWordDetector?.start(finalAccessKey)
                    }
                } else {
                    Log.e(TAG, ">>> Cannot start wake word: Access Key is missing from ALL sources")
                }
                return START_STICKY
            }
            ACTION_STOP_WAKEWORD -> {
                Log.d(TAG, "Stopping wake word detection")
                wakeWordDetector?.stop()
                return START_STICKY
            }
        }

        return START_STICKY
    }

    private fun setupWakeWord() {
        try {
            wakeWordDetector = PorcupineWakeWordDetector(
                context = this,
                onWakeWordDetected = {
                    Log.d(TAG, "Wake Word Detected!")
                    // Emit event to Expo bridge
                    eventListener?.invoke("onWakeWordDetected", emptyMap())
                    // Optionally stop wake word immediately to prevent duplicate triggers
                    wakeWordDetector?.stop()
                },
                onApiFailure = { errorMsg ->
                    Log.e(TAG, "Wake Word API Failure: $errorMsg")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Porcupine wakeup", e)
        }
    }

    private fun initializeAssistant(apiKey: String, modeStr: String, model: String, voiceName: String? = null) {
        Log.d(TAG, "Initializing assistant — mode=$modeStr, model=$model, voice=$voiceName")

        val mode = when (modeStr.uppercase()) {
            "CHAT" -> AssistantMode.CHAT
            else -> AssistantMode.ASSISTANT
        }

        val llm = GeminiClient(apiKey, model)
        val speechService = SpeechServiceBridge(speechCoordinator)

        val assistantSettings = AssistantSettings(
            mode = mode,
            enableSpeech = QueAgentService.isVoiceEnabled,
            enableLogging = QueAgentService.enableLogging,
            voiceName = voiceName,
            conversationWindowSize = 20
        )

        // Instead of creating a duplicate QueAgent that breaks accessibility,
        // we use a Dummy agent for the constructor and inject our own skill router.
        // Wait, QueAssistant constructor REQUIRES a QueAgent. Let's just pass null safely if possible
        // or a stub instance. Since we only want to bridge to the other service.
        assistant = QueAssistant(
            llm = llm,
            agent = QueAgentService.createStubAgent(this, apiKey, model), // Helper method to create structural dummy
            speech = speechService,
            settings = assistantSettings,
            scope = serviceScope
        )
        activeRef = WeakReference(assistant!!)
        isRunning = true

        // Apply voice settings if provided
        val vn = assistantSettings.voiceName
        if (vn != null) {
            Log.d(TAG, "Applying specific voice: $vn")
            speechCoordinator.setVoice(vn)
        }

        // Collect assistant events and forward to Expo bridge
        serviceScope.launch {
            assistant!!.events.collect { event ->
                forwardEvent(event)
            }
        }
        
        // Also send available voices to Expo immediately so it can populate a picker
        sendAvailableVoices()

        Log.i(TAG, "✅ QueAssistant initialized — mode=$modeStr")
    }

    private fun inferGender(voiceName: String): String {
        val name = voiceName.lowercase()
        
        // Google TTS voices — 'f' = female, 'm' = male in the identifier
        val googleFemaleMarkers = setOf("sfg", "iog", "tpf", "iol", "tpd", "iod", "sfb", "sfc", "sfd", "sfe")
        val googleMaleMarkers   = setOf("sfm", "iom", "tpm", "sfj", "sfk", "sfl", "sfn", "iob", "tpg")

        // Check Google-style markers
        googleFemaleMarkers.forEach { marker ->
            if (name.contains("-$marker-") || name.contains("-$marker#") || name.contains("_$marker-")) return "Female"
        }
        googleMaleMarkers.forEach { marker ->
            if (name.contains("-$marker-") || name.contains("-$marker#") || name.contains("_$marker-")) return "Male"
        }

        // Samsung TTS voices
        if (name.contains("svoice") || name.contains("vocalizer")) {
            return when {
                name.contains("zira") || name.contains("heera") || 
                name.contains("susan") || name.contains("kate") -> "Female"
                name.contains("david") || name.contains("mark") || 
                name.contains("james") -> "Male"
                else -> "Neutral"
            }
        }

        // Generic fallback — explicit keywords
        return when {
            name.contains("female") || name.contains("woman") || name.contains("girl") -> "Female"
            name.contains("male")   || name.contains("man")   || name.contains("boy")  -> "Male"
            else -> "Neutral"
        }
    }

    private fun sendAvailableVoices() {
        val voices = speechCoordinator.getAvailableVoices().map { voice ->
            Log.d(TAG, "VOICE_NAME_DEBUG: ${voice.name} (${voice.locale})")
            val gender = inferGender(voice.name)
            mapOf(
                "id" to voice.name,
                "name" to "${voice.locale.displayLanguage} (${voice.locale.displayCountry}) - ${voice.name}",
                "locale" to voice.locale.toString(),
                "gender" to gender,
                "isNetwork" to voice.isNetworkConnectionRequired
            )
        }
        eventListener?.invoke("onAvailableVoices", mapOf("voices" to voices))
    }

    /**
     * Translate AssistantEvent → Expo bridge event listener call.
     */
    private fun forwardEvent(event: AssistantEvent) {
        try {
            when (event) {
                is AssistantEvent.ThinkingStarted ->
                    eventListener?.invoke("onAssistantThinkingStarted", emptyMap())
                is AssistantEvent.ThinkingEnded ->
                    eventListener?.invoke("onAssistantThinkingEnded", emptyMap())
                is AssistantEvent.ListeningStarted ->
                    eventListener?.invoke("onAssistantListeningStarted", emptyMap())
                is AssistantEvent.ListeningEnded ->
                    eventListener?.invoke("onAssistantListeningEnded", mapOf("transcript" to event.transcript))
                is AssistantEvent.SpeakResponse ->
                    eventListener?.invoke("onAssistantSpeakResponse", mapOf(
                        "text" to event.text,
                        "speak" to event.speak
                    ))
                is AssistantEvent.AgentStarted ->
                    eventListener?.invoke("onAssistantAgentStarted", mapOf("taskDescription" to event.taskDescription))
                is AssistantEvent.AgentNarration ->
                    eventListener?.invoke("onAssistantAgentNarration", mapOf(
                        "message" to event.message,
                        "type" to event.type
                    ))
                is AssistantEvent.AgentQuestion ->
                    eventListener?.invoke("onAssistantAgentQuestion", mapOf(
                        "question" to event.question,
                        "options" to event.options
                    ))
                is AssistantEvent.AgentConfirmation ->
                    eventListener?.invoke("onAssistantAgentConfirmation", mapOf(
                        "summary" to event.summary,
                        "preview" to event.preview
                    ))
                is AssistantEvent.AgentFinished ->
                    eventListener?.invoke("onAssistantAgentFinished", mapOf("summary" to event.summary))
                is AssistantEvent.AgentFailed ->
                    eventListener?.invoke("onAssistantAgentFailed", mapOf("reason" to event.reason))
                is AssistantEvent.Error ->
                    eventListener?.invoke("onAssistantError", mapOf("message" to event.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward event: $event", e)
        }
    }

    private fun shutdownAssistant() {
        wakeWordDetector?.stop()
        wakeWordDetector?.cleanup()
        wakeWordDetector = null
        assistant?.dispose()
        assistant = null
        activeRef = null
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownAssistant()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Bridge SpeechCoordinator → SpeechService interface */
    private class SpeechServiceBridge(private val coordinator: SpeechCoordinator) : SpeechService {
        override fun speak(text: String) {
            coordinator.speakToUser(text)
        }
    }
}

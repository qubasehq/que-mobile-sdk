package com.que.platform.android.service
import com.que.core.engine.QueAgent
import com.que.core.interruption.InterruptionHandler
import com.que.core.model.AgentEvent
import com.que.core.model.AgentSettings
import com.que.core.model.AgentState
import com.que.core.registry.ServiceManager
import com.que.core.service.Action
import com.que.core.service.Agent
import com.que.core.service.SpeechService
import com.que.core.service.UserGuidance
import com.que.core.util.AgentLogger
import com.que.platform.android.engine.AgentNotificationManager
import com.que.platform.android.engine.AndroidFileSystem
import com.que.platform.android.engine.QuePerceptionEngine
import com.que.platform.android.engine.SpeechCoordinator
import com.que.platform.android.overlay.AndroidUserGuidance
import com.que.platform.android.overlay.CosmicOverlayController
import com.que.platform.android.util.AndroidEventMonitor
import com.que.platform.android.util.AndroidIntentRegistry
import com.que.platform.android.util.AppLauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.view.WindowManager
import com.que.actions.AndroidActionExecutor
import com.que.llm.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Callback for bidirectional agent events (question, narration, confirmation).
 */
typealias AgentEventListener = (eventType: String, data: Map<String, Any?>) -> Unit

/**
 * A Foreground Service responsible for hosting and running the AI Agent.
 * Based on Blurr's AgentService architecture for reliability and proper lifecycle management.
 */
class QueAgentService : Service() {


    
    // Dedicated coroutine scope tied to the service's lifecycle
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Task queue for managing multiple tasks
    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    
    // Agent and its dependencies
    private lateinit var agent: QueAgent
    private lateinit var apiKey: String
    private lateinit var model: String
    private var maxSteps: Int = 30
    private var enablePredictivePlanning: Boolean = false
    private lateinit var speechCoordinator: SpeechCoordinator
    private var guidance: AndroidUserGuidance? = null
    private lateinit var notificationManager: AgentNotificationManager
    private var interruptionHandler: com.que.core.interruption.InterruptionHandler? = null

    companion object {
        private const val TAG = "QueAgentService"
        private const val ACTION_STOP_SERVICE = "com.que.platform.android.ACTION_STOP_SERVICE"
        private const val ACTION_PAUSE_SERVICE = "com.que.platform.android.ACTION_PAUSE_SERVICE"
        private const val ACTION_RESUME_SERVICE = "com.que.platform.android.ACTION_RESUME_SERVICE"
        const val ACTION_VOICE_ASSIST = "com.que.platform.android.ACTION_VOICE_ASSIST"

        private const val EXTRA_TASK = "com.que.platform.android.EXTRA_TASK"
        private const val EXTRA_API_KEY = "com.que.platform.android.EXTRA_API_KEY"
        private const val EXTRA_MODEL = "com.que.platform.android.EXTRA_MODEL"
        private const val EXTRA_MAX_STEPS = "com.que.platform.android.EXTRA_MAX_STEPS"
        private const val EXTRA_ENABLE_PREDICTIVE_PLANNING = "com.que.platform.android.EXTRA_ENABLE_PREDICTIVE_PLANNING"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPaused: Boolean = false
            private set

        @Volatile
        var currentTask: String? = null
            private set

        @Volatile
        var currentStateName: String = "Idle"
            private set
            
        @Volatile var isVoiceEnabled = true // Enabled by default for native TTS
        @Volatile
        var isAutonomousMode: Boolean = true

        @Volatile var enablePredictivePlanning: Boolean = false
        @Volatile var enableAdaptiveLearning: Boolean = true
        @Volatile var retryFailedActions: Boolean = true
        @Volatile var maxRetries: Int = 3
        @Volatile var maxFailures: Int = 3
        @Volatile var llmTimeoutMs: Long = 30000
        @Volatile var includeScreenshots: Boolean = true
        @Volatile var enableLogging: Boolean = true

        lateinit var boxStore: io.objectbox.BoxStore
            private set
            
        @Volatile
        private var isDbInitialized: Boolean = false

            
        // Securely hold API key in memory to avoid passing via Intent extras (which can be dumped)
        private var sessionApiKey: String? = null

        // External state listener (for Expo module or other consumers)
        private var stateListener: ((stateName: String, message: String) -> Unit)? = null

        // External event listener for bidirectional communication
        private var agentEventListener: AgentEventListener? = null
        
        // Weak reference to active agent for replyToAgent
        private var activeAgentRef: WeakReference<QueAgent>? = null

        fun setStateListener(listener: ((stateName: String, message: String) -> Unit)?) {
            stateListener = listener
        }

        fun setAgentEventListener(listener: AgentEventListener?) {
            agentEventListener = listener
        }

        fun setApiKey(apiKey: String) {
            sessionApiKey = apiKey
        }

        suspend fun listCloudModels(): List<com.que.core.service.ModelInfo> {
            val key = sessionApiKey ?: return emptyList()
            return com.que.llm.GeminiClient(key).listModels()
        }

        /**
         * Reply to the agent when it's waiting for user input (ask_user/confirm).
         */
        fun replyToAgent(reply: String) {
            val agent = activeAgentRef?.get()
            if (agent != null) {
                AgentLogger.d(TAG, "Forwarding user reply to agent: $reply")
                agent.resumeWithUserReply(reply)
            } else {
                AgentLogger.e(TAG, "Cannot reply: no active agent")
            }
        }

        private fun notifyStateChange(stateName: String, message: String = "") {
            currentStateName = stateName
            stateListener?.invoke(stateName, message)
        }

        /**
         * Start the service with a task
         */
        fun start(context: Context, task: String, apiKey: String = "", model: String = "gemini-2.5-flash", maxSteps: Int = 30, enablePredictivePlanning: Boolean = false) {
            AgentLogger.d(TAG, "Starting service with task: $task, maxSteps: $maxSteps")
            // Store key in memory and clear it later. This prevents it from appearing in system dumps blocks.
            // Only override if a non-empty key is provided (supports pre-set via setApiKey())
            if (apiKey.isNotEmpty()) {
                sessionApiKey = apiKey
            }
            
            val intent = Intent(context, QueAgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
                putExtra(EXTRA_MODEL, model)
                putExtra(EXTRA_MAX_STEPS, maxSteps)
                putExtra(EXTRA_ENABLE_PREDICTIVE_PLANNING, enablePredictivePlanning)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the service
         */
        fun stop(context: Context) {
            AgentLogger.d(TAG, "External stop request received.")
            val intent = Intent(context, QueAgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        /**
         * Pause the service
         */
        fun pause(context: Context) {
            AgentLogger.d(TAG, "External pause request received.")
            val intent = Intent(context, QueAgentService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            context.startService(intent)
        }

        /**
         * Resume the service
         */
        fun resume(context: Context) {
            AgentLogger.d(TAG, "External resume request received.")
            val intent = Intent(context, QueAgentService::class.java).apply {
                action = ACTION_RESUME_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        
        // Initialize ObjectBox
        if (!isDbInitialized) {
            boxStore = com.que.platform.android.db.entities.MyObjectBox.builder()
                .androidContext(this.applicationContext)
                .build()
            isDbInitialized = true
            Log.d(TAG, "✓ ObjectBox initialized")
        }

        // Initialize notification manager
        notificationManager = AgentNotificationManager(this)
        
        // Initialize speech coordinator
        speechCoordinator = SpeechCoordinator.getInstance(this)
        Log.d(TAG, "✓ Speech coordinator initialized")
        
        // Initialize interruption handler
        interruptionHandler = com.que.core.interruption.InterruptionHandler()
        Log.d(TAG, "✓ Interruption handler initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        // Unconditionally satisfy Android's strict foreground start rules to prevent ForegroundServiceDidNotStartInTimeException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
            
            startForeground(
                AgentNotificationManager.NOTIFICATION_ID, 
                notificationManager.buildForegroundNotification(currentTask?.let { "Running: $it" } ?: "Agent is standing by"),
                serviceType
            )
        } else {
            startForeground(
                AgentNotificationManager.NOTIFICATION_ID, 
                notificationManager.buildForegroundNotification(currentTask?.let { "Running: $it" } ?: "Agent is standing by")
            )
        }

        // Handle service actions
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Received stop action. Stopping service.")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_SERVICE -> {
                Log.i(TAG, "Received pause action.")
                isPaused = true
                if (::agent.isInitialized) {
                    agent.pause()
                }
                notificationManager.updateStatus("Paused")
                return START_STICKY
            }
            ACTION_RESUME_SERVICE -> {
                Log.i(TAG, "Received resume action.")
                isPaused = false
                if (::agent.isInitialized) {
                    agent.resume()
                }
                notificationManager.updateStatus("Resumed")
                return START_STICKY
            }
            ACTION_VOICE_ASSIST -> {
                Log.i(TAG, "Received VOICE ASSIST action (Power button long-press)")
                // Notify Expo bridge
                agentEventListener?.invoke("onAssistActivated", mapOf())
                
                // If we're not already running, play an acknowledgment sound or speak
                if (!isRunning) {
                    speechCoordinator.speakToUser("I'm listening.")
                }
                return START_STICKY
            }
        }

        // Extract configuration
        val task = intent?.getStringExtra(EXTRA_TASK)
        val apiKeyFromIntent = sessionApiKey // Retrieve from memory
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: "gemini-2.0-flash"
        val maxStepsFromIntent = intent?.getIntExtra(EXTRA_MAX_STEPS, 30)
        val enablePredictivePlanningFromIntent = intent?.getBooleanExtra(EXTRA_ENABLE_PREDICTIVE_PLANNING, false) ?: false

        if (task == null || apiKeyFromIntent == null) {
            AgentLogger.e(TAG, "Missing task or API Key (expired session?)")
            stopSelf()
            return START_NOT_STICKY
        }
        
        this.apiKey = apiKeyFromIntent
        sessionApiKey = null // Clear static reference immediately for security
        this.model = model
        this.maxSteps = maxStepsFromIntent ?: 30
        this.enablePredictivePlanning = enablePredictivePlanningFromIntent

        // Add new task to the queue (Deduplicate only if NOT currently running or NOT the exact current task being processed)
        // Actually, we should allow adding if it's a "Retry" - but how to distinguish?
        // Simple fix: if task is already in queue, ignore. If it's the current task, only allow if the agent has finished.
        if (task.isNotBlank()) {
            val isAlreadyQueued = taskQueue.contains(task)
            val isCurrentlyRunning = isRunning && task == currentTask
            
            if (isAlreadyQueued) {
                 Log.d(TAG, "Task already in queue: $task")
            } else if (isCurrentlyRunning) {
                 Log.d(TAG, "Task matches current ACTIVE task. Ignoring duplicate: $task")
            } else {
                 Log.d(TAG, "Adding task to queue: $task")
                 taskQueue.add(task)
            }
        }

        // If the agent is not already processing tasks, start the loop
        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            isRunning = true 

            serviceScope.launch {
                processTaskQueue()
            }
        }
        return START_STICKY
    }

    private suspend fun processTaskQueue() {
        Log.i(TAG, "=== STARTING TASK PROCESSING LOOP ===")

        // Initialize agent if not already done
        if (!::agent.isInitialized) {
            try {
                Log.d(TAG, "Agent not initialized, initializing now...")
                initializeAgent()
                attachStateMonitoring()
                Log.i(TAG, "✅ Agent initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize agent", e)
                stopSelf()
                return
            }
        }

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.poll() ?: continue
            currentTask = task

            // ALWAYS Start/Restart cosmic overlay service for each task
            // This ensures it reappears even if manually closed between tasks
            serviceScope.launch {
                try {
                    val overlayIntent = Intent(this@QueAgentService, CosmicOverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(overlayIntent)
                    } else {
                        startService(overlayIntent)
                    }
                    Log.d(TAG, "✓ Ensured CosmicOverlayService is running for task: $task")
                    
                    val accessibilityService = com.que.core.registry.ServiceManager.getService<QueAccessibilityService>()
                    if (accessibilityService != null) {
                        accessibilityService.showCosmicOverlay()
                    }
                    
                    CosmicOverlayService.addLog("[TASK] Starting: $task")
                    CosmicOverlayService.updateState(com.que.core.model.AgentState.Thinking())
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start cosmic overlay", e)
                }
            }

            Log.i(TAG, "=== EXECUTING TASK ===")
            Log.i(TAG, "Task: $task")
            
            // Update notification for the new task
            // Update notification for the new task
            notificationManager.updateStatus("Running: $task")
            CosmicOverlayService.addLog("[TASK] Starting: $task")

            try {
                Log.d(TAG, "Calling agent.run()...")
                speechCoordinator.speakToUser("I'm starting my engine")
                val finalState = agent.run(task)
                
                when (finalState) {
                    is com.que.core.model.AgentState.Finished -> {
                        Log.i(TAG, "✅ Task completed successfully: $task")
                        CosmicOverlayService.addLog("[SUCCESS] Task completed: $task")
                        notificationManager.updateStatus("Task Completed")
                    }
                    is com.que.core.model.AgentState.Error -> {
                        Log.e(TAG, "❌ Task failed: ${finalState.message}", finalState.cause)
                        CosmicOverlayService.addLog("[ERROR] Task failed: ${finalState.message}")
                        notificationManager.updateStatus("Task Failed")
                    }
                    else -> {
                         Log.w(TAG, "⚠️ Task ended in unexpected state: $finalState")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Task failed with exception: $task", e)
                CosmicOverlayService.addLog("[ERROR] Task failed: ${e.message}")
             }
        }

        Log.i(TAG, "=== TASK QUEUE EMPTY, STOPPING SERVICE ===")
        // Give the final TTS (Task Completed/Failed) time to play before shutting down
        serviceScope.launch {
            // Wait for speech to finish before stopping service
            kotlinx.coroutines.delay(6000)
            stopSelf()
        }
    }

    private fun initializeAgent() {
        Log.d(TAG, "=== INITIALIZING AGENT COMPONENTS ===")
        Log.d(TAG, "API Key: ${apiKey.take(10)}...")
        Log.d(TAG, "Model: $model")
        Log.d(TAG, "Max Steps: $maxSteps")
        
        // Create settings
        val settings = com.que.core.model.AgentSettings(
            maxSteps = maxSteps,
            maxRetries = QueAgentService.maxRetries,
            maxFailures = QueAgentService.maxFailures,
            enableLogging = QueAgentService.enableLogging,
            model = model,
            llmTimeoutMs = QueAgentService.llmTimeoutMs,
            includeScreenshots = QueAgentService.includeScreenshots,
            retryFailedActions = QueAgentService.retryFailedActions,
            enableAdaptiveLearning = QueAgentService.enableAdaptiveLearning,
            enablePredictivePlanning = QueAgentService.enablePredictivePlanning,
            isAutonomousMode = QueAgentService.isAutonomousMode
        )
        Log.d(TAG, "✓ Agent settings created")
        
        val perception = QuePerceptionEngine(this)
        Log.d(TAG, "✓ Perception engine created")
        
        val fileSystem = AndroidFileSystem(this)
        Log.d(TAG, "✓ File system created")
        
        val intentRegistry = AndroidIntentRegistry(this)
        Log.d(TAG, "✓ Intent registry created")
        
        val gestureController = ServiceGestureControllerWrapper()
        Log.d(TAG, "✓ Gesture controller created")

        val appLauncher = AppLauncher(this)
        Log.d(TAG, "✓ App launcher created")
        
        val eventMonitor = AndroidEventMonitor()
        
        val executor = AndroidActionExecutor(gestureController, intentRegistry, fileSystem, this, appLauncher, eventMonitor)
        Log.d(TAG, "✓ Action executor created with launcher and event monitor")
        
        val llm = GeminiClient(apiKey, model)
        Log.d(TAG, "✓ LLM client created")
        
        val speechService = SpeechServiceWrapper(speechCoordinator)
        
        // Initialize UserGuidance
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        guidance = AndroidUserGuidance(this, windowManager)
        
        val profileLearner = try { 
            Log.d(TAG, "Creating profile learner...")
            com.que.platform.android.db.ProfileLearner(boxStore) 
        } catch(e: Exception) { 
            Log.e(TAG, "Failed to create profile learner", e)
            null 
        }
        
        val historyTracker = try { 
            Log.d(TAG, "Creating history tracker with boxStore: $boxStore")
            com.que.platform.android.db.TaskHistoryRepository(boxStore, profileLearner) 
        } catch(e: Exception) { 
            Log.e(TAG, "Failed to create history tracker", e)
            null 
        }
        
        if (historyTracker == null) {
            Log.e(TAG, "CRITICAL: History tracker is null! Database might be corrupt or inaccessible.")
        }
        
        agent = QueAgent(
            context = this, 
            perception = perception, 
            executor = executor, 
            llm = llm, 
            fileSystem = fileSystem, 
            settings = settings, 
            speech = speechService,
            guidance = guidance,
            historyTracker = historyTracker
        )
        activeAgentRef = WeakReference(agent)
        Log.d(TAG, "✓ QueAgent instance created")
        
        // Forward agent state to overlay service
        // Forward agent state to overlay service
        Log.d(TAG, "=== AGENT INITIALIZATION COMPLETE ===")
    }

    private fun attachStateMonitoring() {
        if (!::agent.isInitialized) return
        
        serviceScope.launch {
            agent.state.collect { state ->
                Log.d(TAG, "Agent state changed: $state")
                CosmicOverlayService.updateState(state)
                
                // Update system-wide cosmic overlay
                val visualState = when (state) {
                    is com.que.core.model.AgentState.Perceiving -> CosmicOverlayController.AgentVisualState.IDLE
                    is com.que.core.model.AgentState.Thinking -> CosmicOverlayController.AgentVisualState.THINKING
                    is com.que.core.model.AgentState.Acting -> CosmicOverlayController.AgentVisualState.ACTING
                    is com.que.core.model.AgentState.Finished -> CosmicOverlayController.AgentVisualState.SUCCESS
                    is com.que.core.model.AgentState.Error -> CosmicOverlayController.AgentVisualState.ERROR
                    else -> CosmicOverlayController.AgentVisualState.IDLE
                }
                
                val accessibilityService = com.que.core.registry.ServiceManager.getService<QueAccessibilityService>()
                if (accessibilityService != null) {
                    accessibilityService.setCosmicState(visualState)
                }
                
                // Log state changes + notify external listeners
                when (state) {
                    is com.que.core.model.AgentState.Perceiving -> {
                        CosmicOverlayService.addLog("[PERCEIVING] Analyzing screen...")
                        notificationManager.updateStatus("Analyzing screen...")
                        notifyStateChange("Perceiving")
                    }
                    is com.que.core.model.AgentState.Thinking -> {
                        CosmicOverlayService.addLog("[THINKING] Processing with LLM...")
                        notificationManager.updateStatus("Thinking...")
                        notifyStateChange("Thinking")
                    }
                    is com.que.core.model.AgentState.Acting -> {
                        CosmicOverlayService.addLog("[ACTING] ${state.actionDescription}")
                        notificationManager.updateStatus("Acting: ${state.actionDescription.take(20)}...")
                        notifyStateChange("Acting", state.actionDescription)
                        speechCoordinator.speakToUser(state.actionDescription)
                    }
                    is com.que.core.model.AgentState.WaitingForUser -> {
                        CosmicOverlayService.addLog("[WAITING] ${state.reason}: ${state.question}")
                        notificationManager.updateStatus("Waiting for user...")
                        notifyStateChange("WaitingForUser", state.question)
                        if (isVoiceEnabled) speechCoordinator.speakToUser(state.question)
                    }
                    is com.que.core.model.AgentState.Finished -> {
                        CosmicOverlayService.addLog("[FINISHED] ${state.result}")
                        notifyStateChange("Finished", state.result)
                        // Voice trigger: task completed
                        if (isVoiceEnabled) speechCoordinator.speakToUser("Done! Here's what happened: ${state.result}")
                    }
                    is com.que.core.model.AgentState.Error -> {
                        CosmicOverlayService.addLog("[ERROR] ${state.message}")
                        notifyStateChange("Error", state.message)
                        // Voice trigger: task failed
                        speechCoordinator.speakToUser("I ran into a problem: ${state.message}")
                    }
                    else -> {}
                }
            }
        }
        
        // Forward agent events for bidirectional communication
        serviceScope.launch {
            agent.events.collect { event ->
                when (event) {
                    is com.que.core.model.AgentEvent.UserQuestionAsked -> {
                        // Show interactive question panel in overlay
                        CosmicOverlayService.showQuestion(
                            question = event.question,
                            options = event.options,
                            onReply = { reply -> QueAgentService.replyToAgent(reply) }
                        )
                        // Also notify Expo bridge
                        agentEventListener?.invoke("onUserQuestion", mapOf(
                            "question" to event.question,
                            "options" to event.options
                        ))
                    }
                    is com.que.core.model.AgentEvent.Narration -> {
                        // Show animated narration banner in overlay
                        CosmicOverlayService.showNarration(event.message, event.type)
                        
                        agentEventListener?.invoke("onNarration", mapOf(
                            "message" to event.message,
                            "type" to event.type
                        ))
                        if (isVoiceEnabled) speechCoordinator.speakToUser(event.message)
                    }
                    is com.que.core.model.AgentEvent.ConfirmationRequired -> {
                        // Show interactive confirmation panel in overlay
                        CosmicOverlayService.showConfirmation(
                            summary = event.summary,
                            actionPreview = event.actionPreview,
                            onReply = { reply -> QueAgentService.replyToAgent(reply) }
                        )
                        
                        agentEventListener?.invoke("onConfirmationRequired", mapOf(
                            "summary" to event.summary,
                            "actionPreview" to event.actionPreview
                        ))
                    }
                    is com.que.core.model.AgentEvent.UserReplied -> {
                        CosmicOverlayService.addLog("[USER REPLY] ${event.reply}")
                    }
                    is com.que.core.model.AgentEvent.TaskDecomposed -> {
                        CosmicOverlayService.addLog("[PLAN] ${event.steps.size} steps decomposed")
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        
        // Create explicit dispose
        if (::agent.isInitialized) {
            agent.dispose()
        }

        // Hide system-wide overlay
        val accessibilityService = com.que.core.registry.ServiceManager.getService<QueAccessibilityService>()
        if (accessibilityService != null) {
            accessibilityService.hideCosmicOverlay()
        } else {
            // Fallback: Stop cosmic overlay service directly
            try {
                val overlayIntent = Intent(this, CosmicOverlayService::class.java)
                stopService(overlayIntent)
                Log.d(TAG, "✓ Stopped CosmicOverlayService directly as fallback")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop cosmic overlay service", e)
            }
        }
        
        // Shutdown speech
        if (::speechCoordinator.isInitialized) {
            speechCoordinator.shutdown()
        }
        
        // Destroy guidance overlay
        guidance?.destroy()
        guidance = null
        
        // Reset status
        isRunning = false
        currentTask = null
        taskQueue.clear()
        
        // Cancel coroutine scope
        serviceScope.cancel()
        
        Log.i(TAG, "Service destroyed and all resources cleaned up.")
    }

    override fun onBind(intent: Intent?): IBinder? = null


    /**
     * Wraps SpeechCoordinator to implement SpeechService interface
     */
    private class SpeechServiceWrapper(private val coordinator: SpeechCoordinator) : SpeechService {
        override fun speak(text: String) {
            coordinator.speakToUser(text)
        }
    }

}

package com.que.platform.android

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
import com.que.platform.android.CosmicOverlayController
import com.que.core.QueAgent
import com.que.llm.GeminiClient
import com.que.core.AgentLogger
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
    private var interruptionHandler: com.que.core.InterruptionHandler? = null

    companion object {
        private const val TAG = "QueAgentService"
        private const val ACTION_STOP_SERVICE = "com.que.platform.android.ACTION_STOP_SERVICE"
        private const val ACTION_PAUSE_SERVICE = "com.que.platform.android.ACTION_PAUSE_SERVICE"
        private const val ACTION_RESUME_SERVICE = "com.que.platform.android.ACTION_RESUME_SERVICE"

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
            
        // Securely hold API key in memory to avoid passing via Intent extras (which can be dumped)
        private var sessionApiKey: String? = null

        /**
         * Start the service with a task
         */
        fun start(context: Context, task: String, apiKey: String, model: String = "gemini-2.5-flash", maxSteps: Int = 30, enablePredictivePlanning: Boolean = false) {
            AgentLogger.d(TAG, "Starting service with task: $task, maxSteps: $maxSteps")
            // Store key in memory and clear it later. This prevents it from appearing in system dumps blocks.
            sessionApiKey = apiKey
            
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
        
        // Initialize notification manager
        notificationManager = AgentNotificationManager(this)
        
        // Initialize speech coordinator
        speechCoordinator = SpeechCoordinator.getInstance(this)
        Log.d(TAG, "✓ Speech coordinator initialized")
        
        // Initialize interruption handler
        interruptionHandler = com.que.core.InterruptionHandler()
        Log.d(TAG, "✓ Interruption handler initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

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
        }

        // Extract configuration
        val task = intent?.getStringExtra(EXTRA_TASK)
        val apiKeyFromIntent = sessionApiKey // Retrieve from memory
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: "gemini-2.5-flash"
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

        // Add new task to the queue (Deduplicate)
        if (task.isNotBlank()) {
            if (task == currentTask || taskQueue.contains(task)) {
                 Log.d(TAG, "Task matches current or queued task. Ignoring duplicate: $task")
            } else {
                 Log.d(TAG, "Adding task to queue: $task")
                 taskQueue.add(task)
            }
        }

        // If the agent is not already processing tasks, start the loop
        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            serviceScope.launch {
                processTaskQueue()
            }
        } else {
            if (isRunning) Log.d(TAG, "Task added to queue. Processor is already running.")
            else Log.d(TAG, "Service started with no task, waiting for tasks.")
        }

        return START_STICKY
    }

    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true

        Log.i(TAG, "=== STARTING TASK PROCESSING LOOP ===")
        startForeground(AgentNotificationManager.NOTIFICATION_ID, notificationManager.buildForegroundNotification("Agent is starting..."))

            // Forward agent state to overlay service - START IT IMMEDIATELY
            serviceScope.launch {
                // ALWAYS Start cosmic overlay service for visual feedback (logs/status)
                try {
                    val overlayIntent = Intent(this@QueAgentService, CosmicOverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(overlayIntent)
                    } else {
                        startService(overlayIntent)
                    }
                    Log.d(TAG, "✓ Started CosmicOverlayService for feedback")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start cosmic overlay", e)
                }

                val accessibilityService = com.que.core.ServiceManager.getService<QueAccessibilityService>()
                // Also trigger particle effects if available (for fun)
                if (accessibilityService != null) {
                    accessibilityService.showCosmicOverlay()
                }
                
                // Show initial status
                CosmicOverlayService.addLog("[SYSTEM] Starting Agent...")
                CosmicOverlayService.updateState(com.que.core.AgentState.Thinking())
            }


        // Initialize agent if not already done
        if (!::agent.isInitialized) {
            try {
                Log.d(TAG, "Agent not initialized, initializing now...")
                CosmicOverlayService.addLog("[SYSTEM] Initializing AI Brain...")
                initializeAgent()
                attachStateMonitoring()
                Log.i(TAG, "✅ Agent initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize agent", e)
                CosmicOverlayService.addLog("[ERROR] Failed to initialize agent: ${e.message}")
                stopSelf()
                return
            }
        }

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.poll() ?: continue
            currentTask = task

            Log.i(TAG, "=== EXECUTING TASK ===")
            Log.i(TAG, "Task: $task")
            
            // Update notification for the new task
            // Update notification for the new task
            notificationManager.updateStatus("Running: $task")
            CosmicOverlayService.addLog("[TASK] Starting: $task")

            try {
                Log.d(TAG, "Calling agent.run()...")
                speechCoordinator.speakToUser("Starting task")
                val finalState = agent.run(task)
                
                when (finalState) {
                    is com.que.core.AgentState.Finished -> {
                        Log.i(TAG, "✅ Task completed successfully: $task")
                        CosmicOverlayService.addLog("[SUCCESS] Task completed: $task")
                        notificationManager.updateStatus("Task Completed")
                    }
                    is com.que.core.AgentState.Error -> {
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
        stopSelf()
    }

    private fun initializeAgent() {
        Log.d(TAG, "=== INITIALIZING AGENT COMPONENTS ===")
        Log.d(TAG, "API Key: ${apiKey.take(10)}...")
        Log.d(TAG, "Model: $model")
        Log.d(TAG, "Max Steps: $maxSteps")
        
        // Create settings
        val settings = com.que.core.AgentSettings(
            maxSteps = maxSteps,
            maxRetries = 5,
            maxFailures = 5,
            enableLogging = true,
            model = model,
            includeScreenshots = true,
            retryFailedActions = true,
            enableAdaptiveLearning = true,
            enablePredictivePlanning = this.enablePredictivePlanning
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
        
        agent = QueAgent(
            context = this, 
            perception = perception, 
            executor = executor, 
            llm = llm, 
            fileSystem = fileSystem, 
            settings = settings, 
            speech = speechService,
            guidance = guidance
        )
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
                    is com.que.core.AgentState.Perceiving -> CosmicOverlayController.AgentVisualState.IDLE
                    is com.que.core.AgentState.Thinking -> CosmicOverlayController.AgentVisualState.THINKING
                    is com.que.core.AgentState.Acting -> CosmicOverlayController.AgentVisualState.ACTING
                    is com.que.core.AgentState.Finished -> CosmicOverlayController.AgentVisualState.SUCCESS
                    is com.que.core.AgentState.Error -> CosmicOverlayController.AgentVisualState.ERROR
                    else -> CosmicOverlayController.AgentVisualState.IDLE
                }
                
                val accessibilityService = com.que.core.ServiceManager.getService<QueAccessibilityService>()
                if (accessibilityService != null) {
                    accessibilityService.setCosmicState(visualState)
                }
                
                // Log state changes
                when (state) {
                    is com.que.core.AgentState.Perceiving -> {
                        CosmicOverlayService.addLog("[PERCEIVING] Analyzing screen...")
                        notificationManager.updateStatus("Analyzing screen...")
                    }
                    is com.que.core.AgentState.Thinking -> {
                        CosmicOverlayService.addLog("[THINKING] Processing with LLM...")
                        notificationManager.updateStatus("Thinking...")
                    }
                    is com.que.core.AgentState.Acting -> {
                        CosmicOverlayService.addLog("[ACTING] ${state.actionDescription}")
                        notificationManager.updateStatus("Acting: ${state.actionDescription.take(20)}...")
                    }
                    is com.que.core.AgentState.Finished -> {
                        CosmicOverlayService.addLog("[FINISHED] ${state.result}")
                    }
                    is com.que.core.AgentState.Error -> {
                        CosmicOverlayService.addLog("[ERROR] ${state.message}")
                    }
                    else -> {}
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
        val accessibilityService = com.que.core.ServiceManager.getService<QueAccessibilityService>()
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
    private class SpeechServiceWrapper(private val coordinator: SpeechCoordinator) : com.que.core.SpeechService {
        override fun speak(text: String) {
            coordinator.speakToUser(text)
        }
    }

    /**
     * Wraps the static instance access to allow the Executor to be created
     * even if the Service isn't connected yet.
     */
    /**
     * Wraps the static instance access to allow the Executor to be created
     * even if the Service isn't connected yet.
     */
    private class ServiceGestureControllerWrapper : com.que.actions.GestureController {
        private var serviceRef: WeakReference<QueAccessibilityService>? = null
        private val checkInterval = 500L
        
        private class ServiceDisconnectedException(message: String) : Exception(message)

        private suspend fun getService(): QueAccessibilityService {
            // Check if current reference is still valid
            val current = serviceRef?.get()
            if (current != null && current.isConnected) {
                return current
            }
            
            // Clear stale reference
            serviceRef = null
            
            // Wait for new instance
            return waitForService()
        }
        
        private suspend fun waitForService(): QueAccessibilityService {
            repeat(20) { // 10 second timeout
                com.que.core.ServiceManager.getService<QueAccessibilityService>()?.takeIf { it.isConnected }?.let {
                    serviceRef = WeakReference(it)
                    return it
                }
                delay(checkInterval)
            }
            throw ServiceDisconnectedException("Accessibility service unavailable")
        }

        override suspend fun dispatchGesture(path: android.graphics.Path, duration: Long): Boolean {
            return try {
                getService().dispatchGesture(path, duration)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun performGlobal(action: Int): Boolean {
            return try {
                getService().performGlobal(action)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun click(x: Int, y: Int): Boolean {
            return try {
                getService().click(x, y)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
            return try {
                getService().scroll(x1, y1, x2, y2, duration)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun setText(text: String): Boolean {
            return try {
                getService().setText(text)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun openApp(packageName: String): Boolean {
            return try {
                getService().openApp(packageName)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun launchAppByName(appName: String): Boolean {
            return try {
                getService().launchAppByName(appName)
            } catch (e: Exception) {
                false
            }
        }

        override suspend fun speak(text: String): Boolean {
            return try {
                getService().speak(text)
            } catch (e: Exception) {
                false
            }
        }
        
        override suspend fun tap(x: Int, y: Int): Boolean {
            return click(x, y)
        }
        
        override suspend fun longPress(x: Int, y: Int, duration: Long): Boolean {
            return try {
                val path = android.graphics.Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo(x.toFloat(), y.toFloat())
                }
                dispatchGesture(path, duration)
            } catch (e: Exception) {
                false
            }
        }
        
        override suspend fun doubleTap(x: Int, y: Int): Boolean {
            return try {
                click(x, y)
                kotlinx.coroutines.delay(100)
                click(x, y)
            } catch (e: Exception) {
                false
            }
        }
        
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
            return scroll(x1, y1, x2, y2, duration)
        }
    }
}

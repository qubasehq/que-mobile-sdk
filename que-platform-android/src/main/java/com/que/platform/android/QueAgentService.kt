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

    private val TAG = "QueAgentService"
    
    // Dedicated coroutine scope tied to the service's lifecycle
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Task queue for managing multiple tasks
    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    
    // Agent and its dependencies
    private lateinit var agent: QueAgent
    private lateinit var apiKey: String
    private lateinit var model: String
    private var maxSteps: Int = 30
    private lateinit var speechCoordinator: SpeechCoordinator
    private var guidance: AndroidUserGuidance? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "QueAgentServiceChannel"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_TASK = "com.que.platform.android.EXTRA_TASK"
        private const val EXTRA_API_KEY = "com.que.platform.android.EXTRA_API_KEY"
        private const val EXTRA_MODEL = "com.que.platform.android.EXTRA_MODEL"
        private const val EXTRA_MAX_STEPS = "com.que.platform.android.EXTRA_MAX_STEPS"
        private const val ACTION_STOP_SERVICE = "com.que.platform.android.ACTION_STOP_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var currentTask: String? = null
            private set
            
        // Securely hold API key in memory to avoid passing via Intent extras (which can be dumped)
        private var sessionApiKey: String? = null

        /**
         * Start the service with a task
         */
        fun start(context: Context, task: String, apiKey: String, model: String = "gemini-2.5-flash", maxSteps: Int = 30) {
            Log.d("QueAgentService", "Starting service with task: $task, maxSteps: $maxSteps")
            // Store key in memory and clear it later. This prevents it from appearing in system dumps blocks.
            sessionApiKey = apiKey
            
            val intent = Intent(context, QueAgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
                // putExtra(EXTRA_API_KEY, apiKey) // REMOVED for security
                putExtra(EXTRA_MODEL, model)
                putExtra(EXTRA_MAX_STEPS, maxSteps)
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
            Log.d("QueAgentService", "External stop request received.")
            val intent = Intent(context, QueAgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        
        // Create notification channel
        createNotificationChannel()
        
        // Initialize speech coordinator
        speechCoordinator = SpeechCoordinator.getInstance(this)
        Log.d(TAG, "✓ Speech coordinator initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        // Handle stop action
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Extract configuration
        val task = intent?.getStringExtra(EXTRA_TASK)
        val apiKeyFromIntent = sessionApiKey // Retrieve from memory
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: "gemini-2.5-flash"
        val maxStepsFromIntent = intent?.getIntExtra(EXTRA_MAX_STEPS, 30)

        if (task == null || apiKeyFromIntent == null) {
            Log.e(TAG, "Missing task or API Key (expired session?)")
            stopSelf()
            return START_NOT_STICKY
        }
        
        this.apiKey = apiKeyFromIntent
        this.model = model
        this.maxSteps = maxStepsFromIntent ?: 30

        // Add new task to the queue
        if (task.isNotBlank()) {
            Log.d(TAG, "Adding task to queue: $task")
            taskQueue.add(task)
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))

        // Initialize agent if not already done
        if (!::agent.isInitialized) {
            try {
                Log.d(TAG, "Agent not initialized, initializing now...")
                initializeAgent()
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
            notificationManager.notify(NOTIFICATION_ID, createNotification("Running: $task"))
            CosmicOverlayService.addLog("[TASK] Starting: $task")

            try {
                Log.d(TAG, "Calling agent.run()...")
                speechCoordinator.speakToUser("Starting task")
                agent.run(task)
                Log.i(TAG, "✅ Task completed successfully: $task")
                CosmicOverlayService.addLog("[SUCCESS] Task completed: $task")
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
            maxRetries = 3,
            maxFailures = 3,
            enableLogging = true,
            model = model,
            includeScreenshots = true,
            retryFailedActions = true,
            enableAdaptiveLearning = true,
            enablePredictivePlanning = true
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
        
        val executor = AndroidActionExecutor(gestureController, intentRegistry, fileSystem, this, appLauncher)
        Log.d(TAG, "✓ Action executor created with launcher")
        
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
        serviceScope.launch {
            // Show cosmic overlay when service starts
            QueAccessibilityService.instance?.showCosmicOverlay()
            
            agent.state.collect { state ->
                Log.d(TAG, "Agent state changed: $state")
                CosmicOverlayService.updateState(state)
                
                // Update system-wide cosmic overlay
                val visualState = when (state) {
                    is com.que.core.AgentState.Perceiving -> CosmicOverlayController.AgentVisualState.IDLE // Pulse while looking
                    is com.que.core.AgentState.Thinking -> CosmicOverlayController.AgentVisualState.THINKING
                    is com.que.core.AgentState.Acting -> CosmicOverlayController.AgentVisualState.ACTING
                    is com.que.core.AgentState.Finished -> CosmicOverlayController.AgentVisualState.SUCCESS
                    is com.que.core.AgentState.Error -> CosmicOverlayController.AgentVisualState.ERROR
                    else -> CosmicOverlayController.AgentVisualState.IDLE
                }
                QueAccessibilityService.instance?.setCosmicState(visualState)
                
                // Log state changes
                when (state) {
                    is com.que.core.AgentState.Perceiving -> {
                        CosmicOverlayService.addLog("[PERCEIVING] Analyzing screen...")
                    }
                    is com.que.core.AgentState.Thinking -> {
                        CosmicOverlayService.addLog("[THINKING] Processing with LLM...")
                    }
                    is com.que.core.AgentState.Acting -> {
                        CosmicOverlayService.addLog("[ACTING] ${state.actionDescription}")
                    }
                    is com.que.core.AgentState.Finished -> {
                        CosmicOverlayService.addLog("[FINISHED] ${state.result}")
                        // Hide overlay after a delay if finished? 
                        // For now keep it to show success state
                    }
                    is com.que.core.AgentState.Error -> {
                        CosmicOverlayService.addLog("[ERROR] ${state.message}")
                    }
                    else -> {}
                }
            }
        }
        
        Log.d(TAG, "=== AGENT INITIALIZATION COMPLETE ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
        
        // Create explicit dispose
        if (::agent.isInitialized) {
            agent.dispose()
        }

        // Hide system-wide overlay
        QueAccessibilityService.instance?.hideCosmicOverlay()
        
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Que Agent Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val stopIntent = Intent(this, QueAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Que Agent Running")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

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
                QueAccessibilityService.instance?.takeIf { it.isConnected }?.let {
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

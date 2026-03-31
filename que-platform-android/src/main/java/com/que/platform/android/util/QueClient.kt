package com.que.platform.android.util
import com.que.core.engine.QueAgent
import com.que.core.model.AgentState
import com.que.platform.android.engine.AndroidFileSystem
import com.que.platform.android.engine.QuePerceptionEngine
import com.que.platform.android.service.CosmicOverlayService
import com.que.platform.android.service.QueAccessibilityService
import com.que.platform.android.service.ServiceGestureControllerWrapper

import android.content.Context
import android.content.Intent
import android.os.Build
import com.que.actions.AndroidActionExecutor
import com.que.llm.GeminiClient
import com.que.platform.android.db.TaskHistoryRepository
import com.que.platform.android.db.ProfileLearner
import com.que.platform.android.db.ContextResolver
import com.que.platform.android.db.entities.TaskRecord
import com.que.platform.android.db.entities.ActionItem
import com.que.platform.android.service.QueAgentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Main entry point for the QUE Mobile SDK.
 * Provides a simple API to run AI agent tasks.
 */
class QueClient internal constructor(
    private val agent: QueAgent,
    private val context: Context
) {

    val state: StateFlow<com.que.core.model.AgentState> = agent.state

    init {
        // Forward agent state to overlay service
        CoroutineScope(Dispatchers.Main).launch {
            agent.state.collect { state ->
                CosmicOverlayService.updateState(state)
            }
        }
    }

    suspend fun runTask(instruction: String) {
        agent.run(instruction)
    }

    fun stop() {
        agent.stop()
    }

    private val historyRepo by lazy { TaskHistoryRepository(QueAgentService.boxStore) }
    private val contextResolver by lazy { ContextResolver(QueAgentService.boxStore) }

    fun getTaskHistory(limit: Int = 50): List<TaskRecord> {
        return historyRepo.getHistory(limit)
    }

    fun getTaskActions(taskId: Long): List<ActionItem> {
        return historyRepo.getTaskActions(taskId)
    }

    fun clearHistory() {
        historyRepo.clearHistory()
    }

    fun resolveContext(vararg fields: String): Map<String, String> {
        return contextResolver.resolve(*fields)
    }

    class Builder(private val context: Context) {
        private var apiKey: String? = null
        private var model: String = "gemini-2.0-flash"
        private var useLocalModel: Boolean = false
        private var localModelId: String? = null

        fun setApiKey(key: String) = apply { this.apiKey = key }
        fun setModel(model: String) = apply { this.model = model }
        
        /**
         * Use a local on-device LLM model instead of a cloud-based one.
         * The model must be downloaded via ModelDownloadManager first.
         */
        fun setUseLocalModel(modelId: String) = apply { 
            this.useLocalModel = true 
            this.localModelId = modelId
        }

        fun build(): QueClient {
            val key = apiKey ?: throw IllegalArgumentException("API Key is required")
            
            // Start cosmic overlay service
            val overlayIntent = Intent(context, CosmicOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlayIntent)
            } else {
                context.startService(overlayIntent)
            }
            
            // Initialize LLM Client (Local or Gemini)
            val llm: com.que.core.service.LLMClient = if (useLocalModel && localModelId != null) {
                try {
                    val modelInfo = com.que.platform.android.llm.LocalModelRegistry.getModelById(localModelId!!)
                        ?: throw IllegalArgumentException("Local model not found in registry: $localModelId")
                    
                    if (!com.que.platform.android.llm.LocalModelRegistry.isModelDownloaded(context, modelInfo)) {
                        throw IllegalStateException("Local model $localModelId is not downloaded. Use ModelDownloadManager first.")
                    }
                    
                    val modelPath = com.que.platform.android.llm.LocalModelRegistry.getModelPath(context, modelInfo)
                    val localClient = com.que.platform.android.llm.LocalLLMClient(
                        context = context,
                        modelPath = modelPath,
                        chatTemplate = modelInfo.chatTemplate
                    )
                    
                    // Pre-load the model
                    localClient.loadModel()
                    localClient
                } catch (e: Exception) {
                    android.util.Log.e("QueClient", "Failed to initialize local LLM, falling back to Gemini: ${e.message}")
                    GeminiClient(key, model)
                }
            } else {
                GeminiClient(key, model)
            }

            // Initialize components
            val perception = QuePerceptionEngine(context, llm)
            
            // val service = QueAccessibilityService.instance
            
            val fileSystem = AndroidFileSystem(context)
            val intentRegistry = AndroidIntentRegistry(context)
            
            val gestureController = ServiceGestureControllerWrapper()
            val appLauncher = com.que.platform.android.util.AppLauncher(context)
            val eventMonitor = com.que.platform.android.util.AndroidEventMonitor()
            val executor = AndroidActionExecutor(gestureController, intentRegistry, fileSystem, context, appLauncher, eventMonitor)
            
            val profileLearner = try { ProfileLearner(QueAgentService.boxStore) } catch (e: Exception) { null }
            val historyRepo = try { TaskHistoryRepository(QueAgentService.boxStore, profileLearner) } catch (e: Exception) { null }
            
            val agent = QueAgent(
                context = context, 
                perception = perception, 
                executor = executor, 
                llm = llm, 
                fileSystem = fileSystem,
                historyTracker = historyRepo
            )
            
            return QueClient(agent, context)
        }
    }
}


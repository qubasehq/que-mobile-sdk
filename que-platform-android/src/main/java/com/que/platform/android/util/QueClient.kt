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

    class Builder(private val context: Context) {
        private var apiKey: String? = null
        private var model: String = "gemini-2.0-flash"

        fun setApiKey(key: String) = apply { this.apiKey = key }
        fun setModel(model: String) = apply { this.model = model }

        fun build(): QueClient {
            val key = apiKey ?: throw IllegalArgumentException("API Key is required")
            
            // Start cosmic overlay service
            val overlayIntent = Intent(context, CosmicOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlayIntent)
            } else {
                context.startService(overlayIntent)
            }
            
            // Initialize components
            val perception = QuePerceptionEngine(context)
            
            // val service = QueAccessibilityService.instance
            
            val fileSystem = AndroidFileSystem(context)
            val intentRegistry = AndroidIntentRegistry(context)
            
            val gestureController = ServiceGestureControllerWrapper()
            val executor = AndroidActionExecutor(gestureController, intentRegistry, fileSystem, context)
            
            val llm = GeminiClient(key, model)
            
            val agent = QueAgent(context, perception, executor, llm, fileSystem)
            
            return QueClient(agent, context)
        }
    }
}


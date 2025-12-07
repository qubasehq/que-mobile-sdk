package com.que.platform.android

import android.content.Context
import android.content.Intent
import android.os.Build
import com.que.actions.AndroidActionExecutor
import com.que.core.QueAgent
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

    val state: StateFlow<com.que.core.AgentState> = agent.state

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
        private var model: String = "gemini-2.5-flash"

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

/**
 * Wraps the static instance access to allow the Executor to be created 
 * even if the Service isn't connected yet.
 */
private class ServiceGestureControllerWrapper : com.que.actions.GestureController {
    
    private var serviceRef: java.lang.ref.WeakReference<QueAccessibilityService>? = null
    private val checkInterval = 500L
    
    private class ServiceDisconnectedException(message: String) : Exception(message)

    private suspend fun getService(): QueAccessibilityService {
        return serviceRef?.get()?.takeIf { it.isConnected }
            ?: waitForService()
    }
    
    private suspend fun waitForService(): QueAccessibilityService {
        repeat(20) { // 10 second timeout
            QueAccessibilityService.instance?.let {
                serviceRef = java.lang.ref.WeakReference(it)
                return it
            }
            kotlinx.coroutines.delay(checkInterval)
        }
        throw ServiceDisconnectedException("Accessibility service unavailable")
    }

    override fun dispatchGesture(path: android.graphics.Path, duration: Long): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().dispatchGesture(path, duration)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun performGlobalAction(action: Int): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().performGlobalAction(action)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun click(x: Int, y: Int): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().click(x, y)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().scroll(x1, y1, x2, y2, duration)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun setText(text: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().setText(text)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun openApp(packageName: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().openApp(packageName)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun launchAppByName(appName: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().launchAppByName(appName)
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun speak(text: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                getService().speak(text)
            } catch (e: Exception) {
                false
            }
        }
    }
}

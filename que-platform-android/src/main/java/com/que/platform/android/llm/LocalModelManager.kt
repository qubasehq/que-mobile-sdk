package com.que.platform.android.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * High-level orchestrator for local LLM lifecycle.
 * Manages model registry, downloads, and model loading.
 */
class LocalModelManager(private val context: Context) {

    private val TAG = "LocalModelManager"
    
    private val downloadManager = ModelDownloadManager(context)
    private var activeClient: LocalLLMClient? = null
    
    private val _status = MutableStateFlow<LocalLLMState>(LocalLLMState.Idle)
    val status: StateFlow<LocalLLMState> = _status.asStateFlow()
    
    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    /**
     * Get list of all available models.
     */
    fun getAvailableModels(): List<LocalModelInfo> = LocalModelRegistry.getAvailableModels()

    /**
     * Get list of models already downloaded on device.
     */
    fun getDownloadedModels(): List<LocalModelInfo> = LocalModelRegistry.getDownloadedModels(context)

    /**
     * Start downloading a model.
     */
    suspend fun downloadModel(modelId: String) {
        val model = LocalModelRegistry.getModelById(modelId) ?: run {
            Log.e(TAG, "Model not found: $" + "{modelId}")
            return
        }
        downloadManager.downloadModel(model)
    }

    /**
     * Load a model into memory and return a LocalLLMClient.
     * Unloads any previously active model.
     */
    fun loadModel(modelId: String): LocalLLMClient? {
        val modelInfo = LocalModelRegistry.getModelById(modelId) ?: return null
        
        if (!LocalModelRegistry.isModelDownloaded(context, modelInfo)) {
            Log.e(TAG, "Model not downloaded: $" + "{modelId}")
            _status.value = LocalLLMState.Error("Model not downloaded")
            return null
        }

        // RAM Check - rough estimate
        if (!hasEnoughRAM(modelInfo)) {
            Log.w(TAG, "Device may not have enough RAM for $" + "{modelId}")
        }

        unloadModel()
        
        _status.value = LocalLLMState.Loading(modelInfo.name)
        
        val modelPath = LocalModelRegistry.getModelPath(context, modelInfo)
        val client = LocalLLMClient(
            context = context,
            modelPath = modelPath,
            chatTemplate = modelInfo.chatTemplate
        )
        
        try {
            client.loadModel()
            activeClient = client
            _status.value = LocalLLMState.Ready(modelInfo.name)
            return client
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $" + "{e.message}", e)
            _status.value = LocalLLMState.Error("Load failed: $" + "{e.message}")
            return null
        }
    }

    /**
     * Unload the current model from memory.
     */
    fun unloadModel() {
        activeClient?.close()
        activeClient = null
        _status.value = LocalLLMState.Idle
    }

    /**
     * Simple RAM check.
     */
    private fun hasEnoughRAM(model: LocalModelInfo): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableRAM = memoryInfo.totalMem
        // Basic heuristic: need at least 1.5x model size in free RAM
        return availableRAM > (model.sizeBytes * 1.5).toLong()
    }
}

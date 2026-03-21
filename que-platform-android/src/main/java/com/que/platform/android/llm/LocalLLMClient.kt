package com.que.platform.android.llm

import android.content.Context
import android.util.Log
import com.que.core.service.LLMClient
import com.que.core.service.LLMResponse
import com.que.core.service.Message
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.InferenceParameters
import de.kherud.llama.args.MiroStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local on-device LLM client using llama.cpp via de.kherud:llama JNI bindings.
 */
class LocalLLMClient(
    private val context: Context,
    private val modelPath: String,
    private val chatTemplate: ChatTemplate = ChatTemplate.CHATML,
    private val maxTokens: Int = 512,
    private val contextSize: Int = 2048,
    private val nGpuLayers: Int = 0
) : LLMClient {

    private val TAG = "LocalLLMClient"
    private var model: LlamaModel? = null
    
    fun loadModel() {
        if (model != null) return
        
        val nThreads = getOptimalThreadCount()
        Log.i(TAG, "Loading model: $" + "{modelPath}")
        
        // Correct names for v4.2.0: setModel, setCtxSize, setThreads, setGpuLayers
        val params = ModelParameters()
            .setModel(modelPath)
            .setCtxSize(contextSize)
            .setThreads(nThreads)
            .setGpuLayers(nGpuLayers)

        model = LlamaModel(params)
        Log.i(TAG, "Model loaded successfully")
    }

    override suspend fun generate(messages: List<Message>): LLMResponse {
        return withContext(Dispatchers.IO) {
            val currentModel = model ?: throw IllegalStateException("Model not loaded")
            
            val prompt = ChatTemplateFormatter.format(messages, chatTemplate)
            
            // Correct name for v4.2.0 InferenceParameters: setMiroStat (cap S)
            val inferParams = InferenceParameters(prompt)
                .setNPredict(maxTokens)
                .setTemperature(0.4f)
                .setTopP(0.9f)
                .setTopK(40)
                .setMiroStat(MiroStat.DISABLED)
            
            val sb = StringBuilder()
            // generate() returns LlamaIterable which yields LlamaOutput
            for (output in currentModel.generate(inferParams)) {
                sb.append(output.text)
            }
            
            val responseText = sb.toString().trim()
            LLMResponse(text = responseText, json = responseText)
        }
    }

    override suspend fun listModels(): List<com.que.core.service.ModelInfo> {
        return listOf(
            com.que.core.service.ModelInfo(
                name = "local",
                displayName = "Local On-Device Model",
                description = "Running $" + "{modelPath}",
                supportedMethods = listOf("generateContent")
            )
        )
    }

    fun isLoaded(): Boolean = model != null

    fun close() {
        try {
            model?.close()
            model = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing: $" + "{e.message}")
        }
    }
    
    private fun getOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return if (cores >= 8) 6 else if (cores >= 4) 4 else 2
    }
}

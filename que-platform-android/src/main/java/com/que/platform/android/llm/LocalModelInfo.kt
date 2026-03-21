package com.que.platform.android.llm

/**
 * Chat template format used by different model families.
 * Each model requires a specific prompt format for proper instruction following.
 */
enum class ChatTemplate {
    /** Gemma format: &lt;start_of_turn&gt;role\ncontent&lt;end_of_turn&gt; */
    GEMMA,
    /** Llama 3 format with header IDs and eot tokens */
    LLAMA3,
    /** Qwen / ChatML format with im_start / im_end tokens */
    CHATML,
    /** Phi-3 format with user/assistant tags */
    PHI3
}

/**
 * Represents a downloadable GGUF model for local on-device inference.
 * 
 * @property id Unique identifier (usually author/repo/filename)
 * @property name Human-readable model name
 * @property downloadUrl Direct download URL (HuggingFace resolve link)
 * @property filename The .gguf filename
 * @property sizeBytes Expected file size in bytes
 * @property chatTemplate The prompt format this model expects
 * @property description Brief description of model capabilities
 * @property parameterCount Number of model parameters (e.g., 1.1B, 2B, 3.8B)
 * @property quantization Quantization level (e.g., Q4_K_M)
 */
data class LocalModelInfo(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val filename: String,
    val sizeBytes: Long,
    val chatTemplate: ChatTemplate,
    val description: String = "",
    val parameterCount: String = "",
    val quantization: String = "Q4_K_M",
    val stopTokens: List<String> = emptyList()
)

/**
 * Represents the current state of a model download.
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0,
        val etaSeconds: Long = 0
    ) : DownloadState() {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
        val speedMBps: String
            get() = "%.2f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0))
    }
    data class Completed(val modelId: String) : DownloadState()
    data class Error(val modelId: String, val message: String) : DownloadState()
}

/**
 * Represents the current state of the local LLM.
 */
sealed class LocalLLMState {
    object Idle : LocalLLMState()
    data class Loading(val modelName: String) : LocalLLMState()
    data class Ready(val modelName: String) : LocalLLMState()
    data class Error(val message: String) : LocalLLMState()
}

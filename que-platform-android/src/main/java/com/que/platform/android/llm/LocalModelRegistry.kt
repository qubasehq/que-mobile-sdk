package com.que.platform.android.llm

import android.content.Context
import java.io.File

/**
 * Registry of recommended mobile-friendly GGUF models for local inference.
 * All models are quantized (Q4_K_M) for optimal size/performance on mobile.
 */
object LocalModelRegistry {

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelPath(context: Context, model: LocalModelInfo): String {
        return File(getModelsDir(context), model.filename).absolutePath
    }

    fun isModelDownloaded(context: Context, model: LocalModelInfo): Boolean {
        val file = File(getModelsDir(context), model.filename)
        return file.exists() && file.length() > 0
    }

    fun getAvailableModels(): List<LocalModelInfo> = PRESET_MODELS

    fun getModelById(id: String): LocalModelInfo? =
        PRESET_MODELS.find { it.id == id }

    fun getDownloadedModels(context: Context): List<LocalModelInfo> =
        PRESET_MODELS.filter { isModelDownloaded(context, it) }

    fun getSmallestModel(): LocalModelInfo =
        PRESET_MODELS.minByOrNull { it.sizeBytes }!!

    private val IM_END = "<" + "|im_end|" + ">"
    private val IM_ENDOFTEXT = "<" + "|endoftext|" + ">"
    private val END_OF_TURN = "<" + "end_of_turn" + ">"
    private val PHI_END = "<" + "|end|" + ">"

    private val PRESET_MODELS = listOf(
        // TinyLlama — smallest, fastest, good for simple commands
        LocalModelInfo(
            id = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
            name = "TinyLlama 1.1B Chat",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sizeBytes = 669_000_000L,
            chatTemplate = ChatTemplate.CHATML,
            description = "Extremely fast. Best for simple commands.",
            parameterCount = "1.1B",
            quantization = "Q4_K_M",
            stopTokens = listOf(IM_END)
        ),
        // Qwen2.5 1.5B — good balance of speed and reasoning
        LocalModelInfo(
            id = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            name = "Qwen2.5 1.5B Instruct",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_050_000_000L,
            chatTemplate = ChatTemplate.CHATML,
            description = "Good balance of speed and reasoning. Multilingual.",
            parameterCount = "1.5B",
            quantization = "Q4_K_M",
            stopTokens = listOf(IM_END, IM_ENDOFTEXT)
        ),
        // Gemma 2 2B — strong reasoning by Google
        LocalModelInfo(
            id = "bartowski/gemma-2-2b-it-GGUF",
            name = "Gemma 2 2B IT",
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            filename = "gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_500_000_000L,
            chatTemplate = ChatTemplate.GEMMA,
            description = "Strong reasoning by Google. Good instruction following.",
            parameterCount = "2B",
            quantization = "Q4_K_M",
            stopTokens = listOf(END_OF_TURN)
        ),
        // Phi 3.5 Mini — best intelligence, needs more RAM
        LocalModelInfo(
            id = "MaziyarPanahi/Phi-3.5-mini-instruct-GGUF",
            name = "Phi 3.5 Mini Instruct",
            downloadUrl = "https://huggingface.co/MaziyarPanahi/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct.Q4_K_M.gguf",
            filename = "Phi-3.5-mini-instruct.Q4_K_M.gguf",
            sizeBytes = 2_393_000_000L,
            chatTemplate = ChatTemplate.PHI3,
            description = "Best intelligence. Reasoning, code, math. Needs 4GB+ RAM.",
            parameterCount = "3.8B",
            quantization = "Q4_K_M",
            stopTokens = listOf(PHI_END, IM_ENDOFTEXT)
        )
    )
}

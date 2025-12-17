# Local On-Device LLM Integration Guide for Que Mobile SDK

This document outlines the architecture and implementation steps to replace the cloud-based `GeminiClient` with a 100% local, on-device Large Language Model (LLM) using **MediaPipe LLM Inference**.

## 1. Technical Architecture

### Current (Cloud)
`QueAgent` -> `GeminiClient` -> Internet -> Google Cloud (Gemini Pro) -> `AgentOutput`

### Target (Local)
`QueAgent` -> `LocalLLMClient` -> `MediaPipe GenAI Task` -> `GPU/NPU` -> `AgentOutput`

**Benefits:**
*   **Privacy:** No data leaves the device.
*   **Latency:** Inference happens instantly on-device (no network lag).
*   **Cost:** Zero API costs.
*   **Offline:** Works without internet.

## 2. Prerequisites & Dependencies

### Recommendation: Llama.cpp (GGUF Support)
To support **GGUF** models (standard format for Llama 3, Mistral, etc.), we will use the `java-llama.cpp` Android bindings.

**Add to `que-platform-android/build.gradle.kts`:**

```kotlin
dependencies {
    implementation("de.kherud:llama:3.0.0") // Example: Java wrapper for llama.cpp
}
```
*Note: You might need to add the `llama.cpp` prebuilt shared libraries (.so) to your `jniLibs` folder if the wrapper doesn't bundle them for Android.*

### Model Selection: The "Mobile-Friendly" List

**CRITICAL: You cannot run full PC models (like 70B parameters) on a phone.**
You must use **Small Language Models (SLMs)** that are **Quantized (Compressed)** to 4-bit.

**Recommended Models (Tested on Android):**

1.  **Phi-3 Mini (3.8B Parameters) - Q4_K_M**
    *   *Size:* ~2.3 GB
    *   *Speed:* Fast
    *   *Intelligence:* High (Beats Llama 2 7B)
    *   *Best for:* Complex reasoning, following instructions.

2.  **Gemma 2B - Q4_K_M**
    *   *Size:* ~1.5 GB
    *   *Speed:* Very Fast
    *   *Intelligence:* Medium
    *   *Best for:* Simple commands ("Open Camera", "Turn on WiFi").

3.  **TinyLlama 1.1B - Q4_K_M**
    *   *Size:* ~700 MB
    *   *Speed:* Extremely Fast (Real-time)
    *   *Intelligence:* Low (Good for very specific formatted tasks).

**What is "Quantization" (Q4)?**
It reduces the precision of the model from 16-bit to 4-bit. This shrinks the file size by 75% with barely any loss in intelligence. **Always download the `Q4_K_M.gguf` version.**

**Storage:** Download GGUF files to `context.filesDir` or `ExternalStorage`.

## 3. Implementation Steps

### Step 1: Define the Interface
Ensure `LLMClient` (in `que-core`) is flexible enough. It already is, but ensure `generate` accepts a clear prompt.

### Step 2: Implement `LocalLLMClient`

Create `LocalLLMClient.kt` in `que-platform-android` (or a new `que-llm-local` module).

```kotlin
class LocalLLMClient(context: Context, modelPath: String) : LLMClient {

    private var llmInference: LlmInference? = null
    
    init {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .setResultListener { partialResult, done -> 
                // Handle streaming if needed
            }
            .build()
            
        llmInference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun generate(messages: List<Message>): LLMResponse {
        // 1. Convert messages to a single prompt string (Chat Template)
        val prompt = buildChatPrompt(messages)
        
        // 2. Run Inference
        val responseText = llmInference?.generateResponse(prompt) ?: ""
        
        // 3. Return result
        return LLMResponse(text = responseText)
    }
    
    // Crucial: Format prompt correctly for the specific model (e.g. Gemma format)
    private fun buildChatPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role) {
                Role.USER -> sb.append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                Role.MODEL -> sb.append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                Role.SYSTEM -> sb.append("<start_of_turn>system\n${msg.content}<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>model\n") // Trigger generation
        return sb.toString()
    }
    
    fun close() {
        llmInference?.close()
    }
}
```

### Step 3: Injecting into QueAgent
In your DI setup (e.g., `QueAccessibilityService.kt` or `MyApplication.kt`), check a configuration flag.

```kotlin
val llmClient = if (useLocalModel) {
    val modelFile = File(context.filesDir, "gemma-2b-it-gpu-int4.bin")
    if (modelFile.exists()) {
        LocalLLMClient(context, modelFile.absolutePath)
    } else {
        // Fallback or Error: "Model not downloaded"
        throw IllegalStateException("Local model not found. Please download it first.")
    }
} else {
    GeminiClient(apiKey)
}

val agent = QueAgent(..., llm = llmClient, ...)
```kotlin
val llmClient = if (useLocalModel) {
    LocalLLMClient(context, Files.gemmaPath)
} else {
    GeminiClient(apiKey)
}
// For Vision, use Qwen2-VL!
```

## 5. "True Vision" with Local VLM (Advanced)
To give the agent **real eyes** without the cloud, you can use the newly released **Vision-Language Model (VLM)** like **Qwen3-VL-2B-Instruct-Q4_K_M.gguf**.

### How it works
Standard LLMs only take text. VLMs take Text + Image.
**You need these TWO specific files from the Qwen3 repository:**

1.  **The Brain (Quantized Model)**:
    *   Filename: `Qwen3VL-2B-Instruct-Q4_K_M.gguf`
    *   Size: ~1.11 GB
    *   *Why this one?* Best balance of speed/intelligence for mobile.

2.  **The Eyes (Vision Projector)**:
    *   Filename: `mmproj-Qwen3VL-2B-Instruct-F16.gguf`
    *   Size: ~819 MB
    *   *Why this one?* Handles the image connection.

**Total Download Size**: ~2 GB (Even smaller than Qwen2!).

### Implementation Changes
1.  **Load Model with Vision**:
    ```kotlin
    val model = LlamaModel(
        "Qwen3VL-2B-Instruct-Q4_K_M.gguf",
        ModelParameters().setNCtx(4096) // Vision needs context!
    )
    ```
2.  **Pass Image Data**:
    *   Pass bytes to the prompt format (Qwen3-VL specific format):
        ```text
        <|vision_start|><|image_pad|><|vision_end|> Describe this UI screen.
        ```
    *   *Note*: The Java wrapper `de.kherud:llama` might need specific updates to support the `llava_eval_image_embed` function from C++. If not supported yet, stick to Text-Only accessibility tree for now.

## 6. Challenges & Mitigations

### 1. Vision Capability
**Local LLMs are TEXT-ONLY.** They cannot see the screen screenshots.
**Solution:**
*   **Accessibility Tree (Semantic View)**: Use the existing `PerceptionEngine` to dump the accessibility node tree (Labels, Button names, IDs) into text.
*   **Prompt Engineering**: "You are an agent. Here is the list of buttons on screen: [Login, Sign Up]. Which one should I click?"
*   This is faster and more reliable than Vision for native apps, but fails on games or custom UI (Canvas).

### 2. Device Heat & Battery
Running LLMs heats up phones.
*   **Mitigation**: Unload the model (`llmInference.close()`) when the agent is idle for >1 minute. Re-loading takes ~1s.

### 3. APK Size
Do not ship the model in the APK. Implement a "Model Download Screen" in your app's settings.

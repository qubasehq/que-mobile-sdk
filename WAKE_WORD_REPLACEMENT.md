# Wake Word Replacement Strategy: Breaking Free from Restrictions

To remove the "one user restriction" and API key dependency of Picovoice Porcupine, we must replace it with an open-source alternative that runs locally and freely.

## The Problem
**Picovoice Porcupine** is accurate but proprietary. The free tier requires every user to sign up and get a unique AccessKey, which destroys the "plug-and-play" experience for a distributed app.

## The Solution: Open Source Alternatives

### Option A: Vosk (Recommended for Reliability)
Vosk is an offline speech recognition toolkit. We can use it in "Keyword Spotting Mode" for a specific phrase (e.g., "Hey Que").
*   **License**: Apache 2.0 (Free, Commercial use allowed).
*   **Pros**: Robust, actively maintained, works on Android.
*   **Cons**: Slightly larger model size than Porcupine.

### Option B: OpenWakeWord (Recommended for Customization)
A newer TFLite-based model trained specifically for wake words.
*   **Pros**: High accuracy, TFLite format (native to Android).
*   **Cons**: Requires running a TFLite interpreter loop (slightly more code).

## Implementation Guide (Vosk)

### 1. Dependencies
Add Vosk Android Lib to `build.gradle.kts`:
```kotlin
implementation("com.alphacephei:vosk-android:0.3.30")
```

### 2. Model Structure
1.  Download the **Vosk Small Model** (e.g., `vosk-model-small-en-us-0.15`) ~40MB.
2.  Place it in `src/main/assets/model-en-us`.

### 3. Create `VoskWakeWordDetector.kt`
Replace `PorcupineWakeWordDetector.kt` with this:

```kotlin
class VoskWakeWordDetector(context: Context, private val onWakeWordDetected: () -> Unit) {

    private var speechService: org.kaldi.SpeechService? = null
    private var model: Model? = null

    init {
        // Asynchronously load model to avoid blocking UI
        StorageService.unpack(context, "model-en-us", "model", 
            { model ->
                this.model = model
                // Configure recognizer for "Hey Que" with weight 1.0 (strictness)
                val recognizer = Recognizer(model, 16000.0f, "[\"hey que\", \"[unk]\"]")
                speechService = org.kaldi.SpeechService(recognizer, 16000.0f)
                speechService?.addListener(object : RecognitionListener {
                    override fun onResult(hypothesis: String) {
                        if (hypothesis.contains("hey que")) {
                            onWakeWordDetected()
                        }
                    }
                    override fun onPartialResult(hypothesis: String) { 
                         if (hypothesis.contains("hey que")) {
                            speechService?.reset() // Reset to avoid double triggering
                            onWakeWordDetected()
                        }
                    }
                    override fun onError(e: Exception) {}
                    override fun onTimeout() {}
                })
            },
            { exception -> Log.e("Vosk", "Failed to load model: $exception") }
        )
    }

    fun start() {
        speechService?.startListening()
    }

    fun stop() {
        speechService?.stop()
    }
}
```

### 4. Integration
Update `QueConversationalService.kt` to use `VoskWakeWordDetector` instead of `PorcupineManager`.

### 5. Why this fixes it
Vosk models are just files. You bundle them with your App.
*   **No API Keys.**
*   **No Internet.**
*   **No User Limits.**

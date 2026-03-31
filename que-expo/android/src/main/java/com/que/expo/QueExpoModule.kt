package com.que.expo
import com.que.core.service.Agent
import com.que.platform.android.service.QueAccessibilityService
import com.que.platform.android.service.QueAgentService
import com.que.platform.android.service.QueAssistantService
import com.que.platform.android.util.PermissionManager
import com.que.platform.android.util.AssistantActivationHelper
import com.que.platform.android.engine.SpeechCoordinator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.speech.tts.Voice
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

import com.que.platform.android.db.TaskHistoryRepository
import com.que.platform.android.db.ContextResolver
import com.que.platform.android.llm.LocalModelManager
import com.que.platform.android.llm.LocalModelInfo
import kotlinx.coroutines.launch

private fun com.que.platform.android.db.entities.TaskRecord.toMap() = mapOf(
    "id" to id.toDouble(),
    "taskText" to taskText,
    "status" to status,
    "startedAt" to startedAt.toDouble(),
    "completedAt" to completedAt.toDouble(),
    "durationSeconds" to durationSeconds,
    "summary" to summary,
    "errorReason" to errorReason,
    "appsTouched" to appsTouched,
    "tokenCount" to tokenCount.toDouble(),
    "stepCount" to stepCount.toDouble()
)

private fun com.que.platform.android.db.entities.ActionItem.toMap() = mapOf(
    "id" to id.toDouble(),
    "taskId" to taskId.toDouble(),
    "timestamp" to timestamp.toDouble(),
    "description" to description,
    "actionType" to actionType,
    "appName" to appName,
    "success" to success
)

private fun LocalModelInfo.toMap() = mapOf(
    "id" to id,
    "name" to name,
    "sizeBytes" to sizeBytes.toDouble(),
    "description" to description,
    "parameterCount" to parameterCount,
    "quantization" to quantization
)

private fun com.que.core.service.ModelInfo.toMap() = mapOf(
    "name" to name,
    "displayName" to displayName,
    "description" to description,
    "supportedMethods" to supportedMethods
)

class QueExpoModule : Module() {

    companion object {
        private const val TAG = "QueExpoModule"
        private const val STATE_CHANGE_EVENT = "onAgentStateChange"
        private const val USER_QUESTION_EVENT = "onUserQuestion"
        private const val NARRATION_EVENT = "onNarration"
        private const val CONFIRMATION_EVENT = "onConfirmationRequired"
        private const val SPEAKING_DONE_EVENT = "onSpeakingDone"
        private const val ASSIST_ACTIVATED_EVENT = "onAssistActivated"
        private const val VOICE_VOLUME_EVENT = "onVoiceVolumeChanged"
        private const val VOICE_PARTIAL_EVENT = "onVoicePartialTranscript"
        private const val VOICE_FINAL_EVENT = "onVoiceFinalTranscript"
        private const val VOICE_ERROR_EVENT = "onVoiceError"
        private const val VOICE_END_EVENT = "onVoiceEnd"
        private const val ASSISTANT_EVENT = "onAssistantEvent"
        private const val WAKE_WORD_EVENT = "onWakeWordDetected"
        private const val CONSOLE_LOG_EVENT = "onConsoleLog"
    }

    private fun inferGender(voiceName: String): String {
        val name = voiceName.lowercase()
        
        // Google TTS voices — 'f' = female, 'm' = male in the identifier
        val googleFemaleMarkers = setOf("sfg", "iog", "tpf", "iol", "tpd", "iod", "sfb", "sfc", "sfd", "sfe")
        val googleMaleMarkers   = setOf("sfm", "iom", "tpm", "sfj", "sfk", "sfl", "sfn", "iob", "tpg")

        // Check Google-style markers
        googleFemaleMarkers.forEach { marker ->
            if (name.contains("-$marker-") || name.contains("-$marker#") || name.contains("_$marker-")) return "Female"
        }
        googleMaleMarkers.forEach { marker ->
            if (name.contains("-$marker-") || name.contains("-$marker#") || name.contains("_$marker-")) return "Male"
        }

        // Samsung TTS voices
        if (name.contains("svoice") || name.contains("vocalizer")) {
            return when {
                name.contains("zira") || name.contains("heera") || 
                name.contains("susan") || name.contains("kate") -> "Female"
                name.contains("david") || name.contains("mark") || 
                name.contains("james") -> "Male"
                else -> "Neutral"
            }
        }

        // Generic fallback — explicit keywords
        return when {
            name.contains("female") || name.contains("woman") || name.contains("girl") -> "Female"
            name.contains("male")   || name.contains("man")   || name.contains("boy")  -> "Male"
            else -> "Neutral"
        }
    }

    override fun definition() = ModuleDefinition {

        Name("QueExpoV3")

        Events(
            STATE_CHANGE_EVENT, 
            USER_QUESTION_EVENT, 
            NARRATION_EVENT, 
            CONFIRMATION_EVENT, 
            SPEAKING_DONE_EVENT, 
            ASSIST_ACTIVATED_EVENT,
            VOICE_VOLUME_EVENT,
            VOICE_PARTIAL_EVENT,
            VOICE_FINAL_EVENT,
            VOICE_ERROR_EVENT,
            VOICE_END_EVENT,
            ASSISTANT_EVENT,
            WAKE_WORD_EVENT,
            CONSOLE_LOG_EVENT
        )

        // ─── Permissions ─────────────────────────

        Function("hasAccessibilityPermission") {
            val ctx = appContext.reactContext ?: return@Function false
            PermissionManager.isAccessibilityServiceEnabled(ctx, QueAccessibilityService::class.java)
        }

        Function("hasOverlayPermission") {
            val ctx = appContext.reactContext ?: return@Function false
            PermissionManager.hasOverlayPermission(ctx)
        }

        Function("hasRequiredPermissions") {
            val ctx = appContext.reactContext ?: return@Function false
            PermissionManager.isAccessibilityServiceEnabled(ctx, QueAccessibilityService::class.java) &&
                PermissionManager.hasOverlayPermission(ctx) &&
                PermissionManager.hasAudioPermission(ctx)
        }

        Function("hasAudioPermission") {
            val ctx = appContext.reactContext ?: return@Function false
            PermissionManager.hasAudioPermission(ctx)
        }

        AsyncFunction("requestAccessibilityPermission") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            PermissionManager.requestAccessibilityPermission(ctx)
            null
        }

        AsyncFunction("requestOverlayPermission") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            PermissionManager.requestOverlayPermission(ctx)
            null
        }

        AsyncFunction("requestAudioPermission") {
            val activity = appContext.currentActivity ?: return@AsyncFunction null
            PermissionManager.requestAudioPermission(activity, 1001)
            null
        }

        AsyncFunction("requestPermissions") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            PermissionManager.requestAccessibilityPermission(ctx)
            null
        }

        AsyncFunction("openAccessibilitySettings") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            null
        }

        AsyncFunction("openOverlaySettings") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            null
        }

        // ─── API Key ─────────────────────────────

        Function("setApiKey") { apiKey: String ->
            QueAgentService.setApiKey(apiKey)
            QueAssistantService.setApiKey(apiKey)
            
            // Persist for other services (e.g. QueConversationalService)
            appContext.reactContext?.let { ctx ->
                val prefs = ctx.getSharedPreferences("QuePrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("API_KEY", apiKey).apply()
            }
            
            Log.d(TAG, "API key set and persisted")
        }

        // ─── Agent Control ───────────────────────

        AsyncFunction("startAgent") { task: String, maxSteps: Int, model: String ->
            val ctx = appContext.reactContext
                ?: throw Exception("React context not available")

            if (!PermissionManager.isAccessibilityServiceEnabled(ctx, QueAccessibilityService::class.java)) {
                throw Exception("Accessibility service not enabled. Please enable it in Settings.")
            }

            if (!PermissionManager.hasOverlayPermission(ctx)) {
                throw Exception("Overlay permission not granted. Please grant it in Settings.")
            }

            Log.d(TAG, "Starting agent — task: '$task', maxSteps: $maxSteps, model: $model")

            QueAgentService.setStateListener { stateName, message ->
                try {
                    sendEvent(STATE_CHANGE_EVENT, mapOf(
                        "state" to stateName,
                        "message" to message
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send state event", e)
                }
            }

            QueAgentService.setAgentEventListener { eventType, data ->
                try {
                    when (eventType) {
                        "onUserQuestion" -> {
                            sendEvent(USER_QUESTION_EVENT, mapOf(
                                "question" to (data["question"] as? String ?: ""),
                                "options" to (data["options"] as? List<*>)
                            ))
                        }
                        "onNarration" -> {
                            sendEvent(NARRATION_EVENT, mapOf(
                                "message" to (data["message"] as? String ?: ""),
                                "type" to (data["type"] as? String ?: "progress")
                            ))
                        }
                        "onConfirmationRequired" -> {
                            sendEvent(CONFIRMATION_EVENT, mapOf(
                                "summary" to (data["summary"] as? String ?: ""),
                                "actionPreview" to (data["actionPreview"] as? String ?: "")
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send agent event: $eventType", e)
                }
            }

            QueAgentService.start(
                context = ctx,
                task = task,
                apiKey = "",
                model = model,
                maxSteps = maxSteps
            )
            null
        }

        AsyncFunction("stopAgent") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null

            Log.d(TAG, "Stopping agent")
            QueAgentService.stop(ctx)
            QueAgentService.setStateListener(null)
            QueAgentService.setAgentEventListener(null)

            sendEvent(STATE_CHANGE_EVENT, mapOf(
                "state" to "Stopped",
                "message" to "Agent stopped by user"
            ))
            null
        }

        AsyncFunction("pauseAgent") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            Log.d(TAG, "Pausing agent")
            QueAgentService.pause(ctx)
            null
        }

        AsyncFunction("resumeAgent") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            Log.d(TAG, "Resuming agent")
            QueAgentService.resume(ctx)
            null
        }

        // ─── Bidirectional Communication ─────────

        AsyncFunction("setVoiceEnabled") { enabled: Boolean ->
            Log.d(TAG, "Setting voice enabled via SDK: $enabled")
            QueAgentService.isVoiceEnabled = enabled
            null
        }

        AsyncFunction("setAutonomousMode") { enabled: Boolean ->
            Log.d(TAG, "Setting autonomous mode: $enabled")
            QueAgentService.isAutonomousMode = enabled
            null
        }

        AsyncFunction("setNativeAdvancedSettings") { config: Map<String, Any> ->
            Log.d(TAG, "Setting advanced configurations: $config")
            (config["enablePredictivePlanning"] as? Boolean)?.let { QueAgentService.enablePredictivePlanning = it }
            (config["enableAdaptiveLearning"] as? Boolean)?.let { QueAgentService.enableAdaptiveLearning = it }
            (config["retryFailedActions"] as? Boolean)?.let { QueAgentService.retryFailedActions = it }
            (config["maxRetries"] as? Number)?.let { QueAgentService.maxRetries = it.toInt() }
            (config["maxFailures"] as? Number)?.let { QueAgentService.maxFailures = it.toInt() }
            (config["llmTimeoutMs"] as? Number)?.let { QueAgentService.llmTimeoutMs = it.toLong() }
            (config["includeScreenshots"] as? Boolean)?.let { QueAgentService.includeScreenshots = it }
            (config["enableLogging"] as? Boolean)?.let { QueAgentService.enableLogging = it }
            null
        }

        AsyncFunction("replyToAgent") { reply: String ->
            Log.d(TAG, "User reply to agent: $reply")
            QueAgentService.replyToAgent(reply)
            null
        }

        AsyncFunction("startVoiceRecognition") {
            val ctx = appContext.reactContext ?: throw Exception("React context not available")
            
            val sttManager = com.que.platform.android.util.STTManager(ctx)
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    sttManager.startListening().collect { event ->
                        when (event) {
                            is com.que.platform.android.util.STTEvent.Partial -> 
                                sendEvent(VOICE_PARTIAL_EVENT, mapOf("text" to event.text))
                            is com.que.platform.android.util.STTEvent.Final -> 
                                sendEvent(VOICE_FINAL_EVENT, mapOf("text" to event.text))
                            is com.que.platform.android.util.STTEvent.Volume -> 
                                sendEvent(VOICE_VOLUME_EVENT, mapOf("volume" to event.rmsdB))
                            is com.que.platform.android.util.STTEvent.Error -> 
                                sendEvent(VOICE_ERROR_EVENT, mapOf("error" to event.message))
                            is com.que.platform.android.util.STTEvent.EndOfSpeech ->
                                sendEvent(VOICE_END_EVENT, mapOf("status" to "ended"))
                        }
                    }
                } catch (e: Exception) {
                    sendEvent(VOICE_ERROR_EVENT, mapOf("error" to (e.message ?: "Unknown error")))
                }
            }
            null
        }

        // ─── Memory & Context ────────────────────

        Function("getTaskHistory") { limit: Int ->
            val ctx = appContext.reactContext ?: return@Function emptyList<Map<String, Any>>()
            try {
                val repo = TaskHistoryRepository(QueAgentService.boxStore)
                repo.getHistory(limit).map { it.toMap() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get history", e)
                emptyList<Map<String, Any>>()
            }
        }

        Function("getTaskActions") { taskId: Double ->
            val ctx = appContext.reactContext ?: return@Function emptyList<Map<String, Any>>()
            try {
                val repo = TaskHistoryRepository(QueAgentService.boxStore)
                repo.getTaskActions(taskId.toLong()).map { it.toMap() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get specific task actions", e)
                emptyList<Map<String, Any>>()
            }
        }

        Function("clearHistory") {
            try {
                val repo = TaskHistoryRepository(QueAgentService.boxStore)
                repo.clearHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear history", e)
            }
        }

        Function("resolveContext") { fields: List<String> ->
            try {
                val resolver = ContextResolver(QueAgentService.boxStore)
                resolver.resolve(*fields.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve context", e)
                emptyMap<String, String>()
            }
        }

        AsyncFunction("listCloudModels") { promise: Promise ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val models = QueAgentService.listCloudModels().map { it.toMap() }
                    promise.resolve(models)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to list cloud models", e)
                    promise.resolve(emptyList<Map<String, Any>>())
                }
            }
            null
        }

        // ─── Local Model Management ──────────────

        Function("getAvailableModels") {
            try {
                appContext.reactContext?.let { ctx ->
                    LocalModelManager(ctx).getAvailableModels().map { it.toMap() }
                } ?: emptyList<Map<String, Any>>()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available models", e)
                emptyList<Map<String, Any>>()
            }
        }

        Function("getDownloadedModels") {
            try {
                appContext.reactContext?.let { ctx ->
                    LocalModelManager(ctx).getDownloadedModels().map { it.toMap() }
                } ?: emptyList<Map<String, Any>>()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get downloaded models", e)
                emptyList<Map<String, Any>>()
            }
        }

        AsyncFunction("downloadModel") { modelId: String ->
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            try {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    LocalModelManager(ctx).downloadModel(modelId)
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Download trigger failed", e)
                null
            }
        }

        Function("getAgentState") {
            mapOf(
                "state" to QueAgentService.currentStateName,
                "isRunning" to QueAgentService.isRunning
            )
        }

        // ─── TTS ─────────────────────────────────

        AsyncFunction("speak") { text: String, promise: Promise ->
            val ctx = appContext.reactContext ?: run {
                promise.reject("ERR_CONTEXT", "React context not available", null)
                return@AsyncFunction
            }

            val coordinator = SpeechCoordinator.getInstance(ctx)
            coordinator.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH)

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(100)
                while (coordinator.isSpeaking()) {
                    kotlinx.coroutines.delay(100)
                }
                try {
                    sendEvent(SPEAKING_DONE_EVENT, mapOf("status" to "done"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send speaking done event", e)
                }
                promise.resolve(null)
            }
            null
        }

        Function("stopSpeaking") {
            val ctx = appContext.reactContext
            if (ctx != null) {
                SpeechCoordinator.getInstance(ctx).stop()
            }
        }

        Function("isSpeaking") {
            val ctx = appContext.reactContext
            if (ctx != null) {
                SpeechCoordinator.getInstance(ctx).isSpeaking()
            } else {
                false
            }
        }

        Function("getAvailableVoices") {
            val ctx = appContext.reactContext ?: return@Function emptyList<Map<String, Any>>()
            SpeechCoordinator.getInstance(ctx).getAvailableVoices().map {
                val gender = inferGender(it.name)
                mapOf(
                    "id" to it.name,
                    "name" to "${it.locale.displayLanguage} (${it.locale.displayCountry}) - ${it.name}",
                    "locale" to it.locale.toString(),
                    "gender" to gender,
                    "isNetwork" to it.isNetworkConnectionRequired
                )
            }
        }

        // ─── Assistant Activation ─────────────────

        Function("isDefaultAssistant") {
            val ctx = appContext.reactContext ?: return@Function false
            AssistantActivationHelper.isDefaultAssistant(ctx)
        }

        AsyncFunction("openAssistantSettings") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            AssistantActivationHelper.openAssistantSettings(ctx)
            null
        }

        // ─── QueAssistant (CHAT / ASSISTANT mode) ─

        AsyncFunction("startAssistant") { mode: String, model: String, voiceName: String? ->
            val ctx = appContext.reactContext
                ?: throw Exception("React context not available")

            Log.d(TAG, "Starting assistant — mode: $mode, model: $model, voice: $voiceName")

            QueAssistantService.setAssistantEventListener { eventType, data ->
                try {
                    if (eventType == "onWakeWordDetected") {
                        sendEvent(WAKE_WORD_EVENT, emptyMap())
                    } else {
                        sendEvent(ASSISTANT_EVENT, mapOf(
                            "eventType" to eventType,
                            "data" to data
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send assistant event: $eventType", e)
                }
            }

            QueAssistantService.start(
                context = ctx,
                mode = mode,
                model = model,
                voiceName = voiceName
            )
            null
        }

        AsyncFunction("stopAssistant") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            Log.d(TAG, "Stopping assistant")
            QueAssistantService.stop(ctx)
            QueAssistantService.setAssistantEventListener(null)
            null
        }

        AsyncFunction("sendToAssistant") { text: String ->
            Log.d(TAG, "Sending to assistant: $text")
            QueAssistantService.send(text)
            null
        }

        AsyncFunction("replyToAssistant") { reply: String ->
            Log.d(TAG, "Reply to assistant: $reply")
            QueAssistantService.replyToAssistant(reply)
            null
        }

        AsyncFunction("clearAssistantMemory") {
            Log.d(TAG, "Clearing assistant memory")
            QueAssistantService.clearMemory()
            null
        }

        AsyncFunction("startWakeWord") { accessKey: String ->
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            Log.d(TAG, ">>> startWakeWord called from JS. Key length: ${accessKey.length}, blank: ${accessKey.isBlank()}")
            
            // Persist for other services (only if non-blank)
            if (accessKey.isNotBlank()) {
                val prefs = ctx.getSharedPreferences("QuePrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("PICOVOICE_KEY", accessKey).apply()
                Log.d(TAG, ">>> Persisted PICOVOICE_KEY to QuePrefs")
            } else {
                Log.d(TAG, ">>> Key is blank, not persisting. Will rely on native fallback.")
            }
            
            QueAssistantService.startWakeWord(ctx, accessKey)
            null
        }

        AsyncFunction("stopWakeWord") {
            val ctx = appContext.reactContext ?: return@AsyncFunction null
            Log.d(TAG, "Stopping wake word")
            QueAssistantService.stopWakeWord(ctx)
            null
        }

        Function("getAssistantState") {
            mapOf(
                "isRunning" to QueAssistantService.isRunning,
                "mode" to QueAssistantService.currentMode
            )
        }

        // ─── Lifecycle ───────────────────────────

        OnDestroy {
            QueAgentService.setStateListener(null)
            QueAgentService.setAgentEventListener(null)
            QueAgentService.setGlobalLogListener(null)
            QueAssistantService.setAssistantEventListener(null)
        }

        OnCreate {
            QueAgentService.setGlobalLogListener { message ->
                try {
                    sendEvent(CONSOLE_LOG_EVENT, mapOf("message" to message))
                } catch (e: Exception) {
                    // Ignore if no listeners
                }
            }
        }
    }
}

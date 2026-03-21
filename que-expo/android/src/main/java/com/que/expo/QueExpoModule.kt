package com.que.expo
import com.que.core.service.Agent
import com.que.platform.android.service.QueAccessibilityService
import com.que.platform.android.service.QueAgentService
import com.que.platform.android.util.PermissionManager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
    }

    override fun definition() = ModuleDefinition {

        Name("QueMobileSDK")

        Events(STATE_CHANGE_EVENT, USER_QUESTION_EVENT, NARRATION_EVENT, CONFIRMATION_EVENT)

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
            Log.d(TAG, "API key set (${apiKey.length} chars)")
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

            // Register state listener before starting
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

            // Register agent event listener for bidirectional communication
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
            Log.d(TAG, "Setting voice enabled: $enabled")
            QueAgentService.isVoiceEnabled = enabled
            null
        }

        AsyncFunction("setAutonomousMode") { enabled: Boolean ->
            Log.d(TAG, "Setting autonomous mode: $enabled")
            QueAgentService.isAutonomousMode = enabled
            null
        }

        AsyncFunction("replyToAgent") { reply: String ->
            Log.d(TAG, "User reply to agent: $reply")
            QueAgentService.replyToAgent(reply)
            null
        }

        AsyncFunction("startVoiceRecognition") { promise: Promise ->
            val ctx = appContext.reactContext ?: run {
                promise.reject("ERR_CONTEXT", "React context not available", null)
                return@AsyncFunction
            }
            
            val sttManager = com.que.platform.android.util.STTManager(ctx)
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    sttManager.startListening().collect { transcript ->
                        promise.resolve(transcript)
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_STT", e.message, e)
                }
            }
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

        // ─── Lifecycle ───────────────────────────

        OnDestroy {
            QueAgentService.setStateListener(null)
            QueAgentService.setAgentEventListener(null)
        }
    }
}

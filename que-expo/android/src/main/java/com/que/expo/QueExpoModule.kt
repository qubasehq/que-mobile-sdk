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
                PermissionManager.hasOverlayPermission(ctx)
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

        AsyncFunction("replyToAgent") { reply: String ->
            Log.d(TAG, "User reply to agent: $reply")
            QueAgentService.replyToAgent(reply)
            null
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

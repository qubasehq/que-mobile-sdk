package com.que.platform.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Helper to check and manage Que's status as the default digital assistant.
 * Enables power button long-press → Que activation.
 */
object AssistantActivationHelper {

    private const val TAG = "AssistantHelper"

    /**
     * Check if Que is currently set as the default digital assistant.
     */
    fun isDefaultAssistant(context: Context): Boolean {
        return try {
            val assistant = Settings.Secure.getString(
                context.contentResolver,
                "assistant"
            )
            if (assistant.isNullOrEmpty()) {
                Log.d(TAG, "No default assistant set")
                false
            } else {
                val component = ComponentName.unflattenFromString(assistant)
                val isQue = component?.packageName == context.packageName
                Log.d(TAG, "Default assistant: $assistant, isQue: $isQue")
                isQue
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check default assistant", e)
            false
        }
    }

    /**
     * Deep link user to Settings > Default Apps > Digital Assistant.
     */
    fun openAssistantSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open assistant settings", e2)
            }
        }
    }
}

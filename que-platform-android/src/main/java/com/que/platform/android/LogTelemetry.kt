package com.que.platform.android

import android.util.Log
import com.que.core.Telemetry

/**
 * Android implementation of Telemetry that logs to Logcat.
 * In a real app, this would wrap Firebase Analytics, Sentry, etc.
 */
class LogTelemetry : Telemetry {
    private val TAG = "QueTelemetry"

    override fun trackEvent(eventName: String, params: Map<String, String>) {
        Log.d(TAG, "Event: $eventName | Params: $params")
    }

    override fun trackError(error: Throwable, context: Map<String, String>) {
        Log.e(TAG, "Error: ${error.message} | Context: $context", error)
    }

    override fun log(level: Telemetry.LogLevel, tag: String, message: String, metadata: Map<String, String>) {
        val fullMessage = if (metadata.isNotEmpty()) "$message | Metadata: $metadata" else message
        when (level) {
            Telemetry.LogLevel.DEBUG -> Log.d(tag, fullMessage)
            Telemetry.LogLevel.INFO -> Log.i(tag, fullMessage)
            Telemetry.LogLevel.WARN -> Log.w(tag, fullMessage)
            Telemetry.LogLevel.ERROR -> Log.e(tag, fullMessage)
        }
    }
}

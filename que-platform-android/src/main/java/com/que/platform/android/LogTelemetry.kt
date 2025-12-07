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
}

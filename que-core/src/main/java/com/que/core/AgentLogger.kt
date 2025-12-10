package com.que.core

import android.util.Log

/**
 * Centralized logger that writes to both Android Logcat and the Telemetry system.
 * Replaces ad-hoc Log.d/Log.e calls to ensure comprehensive observability.
 */
object AgentLogger {
    private var telemetry: Telemetry = Telemetry.NoOp
    private var enableDebugLogs: Boolean = true

    fun initialize(telemetry: Telemetry, debug: Boolean = true) {
        this.telemetry = telemetry
        this.enableDebugLogs = debug
    }

    fun d(tag: String, message: String, metadata: Map<String, String> = emptyMap()) {
        if (enableDebugLogs) {
            Log.d(tag, message)
            telemetry.log(Telemetry.LogLevel.DEBUG, tag, message, metadata)
        }
    }

    fun i(tag: String, message: String, metadata: Map<String, String> = emptyMap()) {
        Log.i(tag, message)
        telemetry.log(Telemetry.LogLevel.INFO, tag, message, metadata)
    }

    fun w(tag: String, message: String, metadata: Map<String, String> = emptyMap()) {
        Log.w(tag, message)
        telemetry.log(Telemetry.LogLevel.WARN, tag, message, metadata)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, String> = emptyMap()) {
        Log.e(tag, message, throwable)
        telemetry.log(Telemetry.LogLevel.ERROR, tag, message, metadata)
        if (throwable != null) {
            telemetry.trackError(throwable, metadata + ("tag" to tag) + ("message" to message))
        }
    }
}

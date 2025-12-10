package com.que.core

/**
 * Interface for telemetry and analytics.
 */
interface Telemetry {
    fun trackEvent(eventName: String, params: Map<String, String> = emptyMap())
    fun trackError(error: Throwable, context: Map<String, String> = emptyMap())
    fun log(level: LogLevel, tag: String, message: String, metadata: Map<String, String> = emptyMap())

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    // No-op implementation for default usage
    object NoOp : Telemetry {
        override fun trackEvent(eventName: String, params: Map<String, String>) {}
        override fun trackError(error: Throwable, context: Map<String, String>) {}
        override fun log(level: LogLevel, tag: String, message: String, metadata: Map<String, String>) {}
    }
}

package com.que.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the lifecycle and availability of running services.
 * Replaces direct static access to QueAccessibilityService to prevent null pointer exceptions
 * and race conditions during service restarts.
 */
object ServiceManager {

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private var _accessibilityService: Any? = null
    
    // Abstract interface for the service would be better, but for now we hold the reference safely
    // Casting will be handled by the consumer who knows the type
    
    fun registerAccessibilityService(service: Any) {
        _accessibilityService = service
        _serviceState.value = ServiceState.Connected
    }

    fun unregisterAccessibilityService() {
        _accessibilityService = null
        _serviceState.value = ServiceState.Disconnected
    }

    fun isServiceConnected(): Boolean {
        return _serviceState.value == ServiceState.Connected
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getService(): T? {
        return _accessibilityService as? T
    }
}

sealed class ServiceState {
    object Connected : ServiceState()
    object Disconnected : ServiceState()
}

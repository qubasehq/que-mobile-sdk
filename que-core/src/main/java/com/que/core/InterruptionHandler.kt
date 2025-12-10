package com.que.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

/**
 * Handles various types of interruptions during agent execution.
 */
class InterruptionHandler {
    private val mutex = Mutex()
    private var interruptionState = InterruptionState.NONE
    private var checkpoint: AgentCheckpoint? = null
    
    companion object {
        private const val TAG = "InterruptionHandler"
    }
    
    /**
     * Handle different types of interruptions
     */
    suspend fun handleInterruption(
        type: InterruptionType,
        agent: Agent,
        context: InterruptionContext
    ): InterruptionResponse {
        return mutex.withLock {
            Log.d(TAG, "Handling interruption: $type")
            
            when (type) {
                InterruptionType.USER_PAUSE -> handleUserPause(agent)
                InterruptionType.USER_RESUME -> handleUserResume(agent)
                InterruptionType.USER_CANCEL -> handleUserCancel(agent)
                InterruptionType.SYSTEM_INTERRUPT -> handleSystemInterrupt(agent, context)
                InterruptionType.DEVICE_LOCKED -> handleDeviceLocked(agent)
                InterruptionType.PERMISSION_REVOKED -> handlePermissionRevoked(agent, context)
            }
        }
    }
    
    private suspend fun handleUserPause(agent: Agent): InterruptionResponse {
        interruptionState = InterruptionState.PAUSED
        agent.pause()
        
        // Create a checkpoint when pausing
        checkpoint = agent.createCheckpoint()
        
        return InterruptionResponse(
            action = InterruptionAction.WAIT_FOR_RESUME,
            message = "Agent paused by user",
            shouldContinue = true
        )
    }
    
    private suspend fun handleUserResume(agent: Agent): InterruptionResponse {
        interruptionState = InterruptionState.RESUMED
        agent.resume()
        
        return InterruptionResponse(
            action = InterruptionAction.CONTINUE_EXECUTION,
            message = "Agent resumed by user",
            shouldContinue = true
        )
    }
    
    private suspend fun handleUserCancel(agent: Agent): InterruptionResponse {
        interruptionState = InterruptionState.CANCELLED
        agent.stop()
        
        return InterruptionResponse(
            action = InterruptionAction.STOP_EXECUTION,
            message = "Agent cancelled by user",
            shouldContinue = false
        )
    }
    
    private suspend fun handleSystemInterrupt(agent: Agent, context: InterruptionContext): InterruptionResponse {
        // For system interrupts, we might want to pause and wait
        agent.pause()
        
        return InterruptionResponse(
            action = InterruptionAction.WAIT_FOR_RESUME,
            message = "System interrupt detected: ${context.systemEvent}",
            shouldContinue = true
        )
    }
    
    private suspend fun handleDeviceLocked(agent: Agent): InterruptionResponse {
        // Pause execution when device is locked
        agent.pause()
        
        return InterruptionResponse(
            action = InterruptionAction.WAIT_FOR_UNLOCK,
            message = "Device locked, pausing execution",
            shouldContinue = true
        )
    }
    
    private suspend fun handlePermissionRevoked(agent: Agent, context: InterruptionContext): InterruptionResponse {
        // Stop execution when permissions are revoked
        agent.stop()
        
        return InterruptionResponse(
            action = InterruptionAction.REQUEST_PERMISSIONS,
            message = "Permission revoked: ${context.permission}",
            shouldContinue = false
        )
    }
    
    /**
     * Get the last checkpoint if available
     */
    fun getLastCheckpoint(): AgentCheckpoint? = checkpoint
    
    /**
     * Clear the current interruption state
     */
    fun clearInterruption() {
        interruptionState = InterruptionState.NONE
    }
}

/**
 * Types of interruptions that can occur
 */
enum class InterruptionType {
    USER_PAUSE,
    USER_RESUME,
    USER_CANCEL,
    SYSTEM_INTERRUPT,
    DEVICE_LOCKED,
    PERMISSION_REVOKED
}

/**
 * Current state of interruption handling
 */
enum class InterruptionState {
    NONE,
    PAUSED,
    RESUMED,
    CANCELLED,
    WAITING
}

/**
 * Actions to take in response to an interruption
 */
enum class InterruptionAction {
    CONTINUE_EXECUTION,
    STOP_EXECUTION,
    WAIT_FOR_RESUME,
    WAIT_FOR_UNLOCK,
    REQUEST_PERMISSIONS
}

/**
 * Response from handling an interruption
 */
data class InterruptionResponse(
    val action: InterruptionAction,
    val message: String,
    val shouldContinue: Boolean
)

/**
 * Context information for an interruption
 */
data class InterruptionContext(
    val systemEvent: String? = null,
    val permission: String? = null,
    val errorMessage: String? = null
)
package com.que.core

import kotlinx.coroutines.flow.StateFlow

/**
 * The brain of the operation.
 * The Agent orchestrates the Sense -> Think -> Act loop.
 */
interface Agent {
    /**
     * The current state of the agent.
     * Observes this flow to update UI.
     */
    val state: StateFlow<AgentState>

    /**
     * Starts a new task.
     * @param instruction The natural language instruction from the user.
     */
    suspend fun run(instruction: String): AgentState

    /**
     * Stops the current task immediately.
     */
    fun stop()
    
    /**
     * Pauses the current task.
     */
    fun pause()
    
    /**
     * Resumes a paused task.
     */
    fun resume()
    
    /**
     * Checks if the agent is currently paused.
     */
    fun isPaused(): Boolean
    
    /**
     * Creates a checkpoint of the current agent state.
     */
    suspend fun createCheckpoint(): AgentCheckpoint
    
    /**
     * Restores the agent from a checkpoint.
     */
    suspend fun restoreFromCheckpoint(checkpoint: AgentCheckpoint)
}

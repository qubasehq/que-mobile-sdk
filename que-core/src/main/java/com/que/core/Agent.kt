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
    suspend fun run(instruction: String)

    /**
     * Stops the current task immediately.
     */
    fun stop()
}

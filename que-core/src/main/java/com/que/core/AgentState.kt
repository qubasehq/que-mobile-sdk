package com.que.core

/**
 * Represents the various states of the Agent during its lifecycle.
 */
sealed class AgentState {
    /**
     * Agent is idle and ready for a task.
     */
    data object Idle : AgentState()

    /**
     * Agent is analyzing the screen (Perception).
     */
    data object Perceiving : AgentState()

    /**
     * Agent is waiting for the LLM to decide (Thinking).
     */
    data class Thinking(val context: String = "") : AgentState()

    /**
     * Agent is executing an action (Acting).
     */
    data class Acting(val actionDescription: String) : AgentState()

    /**
     * Agent has completed the task successfully.
     */
    data class Finished(val result: String) : AgentState()

    /**
     * Agent encountered an error and stopped.
     */
    data class Error(val message: String, val cause: Throwable? = null) : AgentState()
}

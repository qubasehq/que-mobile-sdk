package com.que.core.model
import com.que.core.service.Agent

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

    /**
     * Agent is waiting for user input (question, confirmation, etc.).
     * The agent loop is paused until resumeWithUserReply() is called.
     */
    data class WaitingForUser(
        val reason: String,
        val question: String = "",
        val options: List<String>? = null
    ) : AgentState()
}

package com.que.core.model

/**
 * Events emitted by the agent for bidirectional communication.
 * These are consumed by the platform layer (QueAgentService) and forwarded
 * to the Expo/React Native bridge.
 * 
 * All existing AgentState events remain unchanged — these are additional
 * communication events for richer agent-user interaction.
 */
sealed class AgentEvent {
    /**
     * Agent needs to ask the user a question before continuing.
     * The agent loop pauses until the user replies.
     */
    data class UserQuestionAsked(
        val question: String,
        val options: List<String>? = null
    ) : AgentEvent()

    /**
     * Real-time narration of what the agent is doing.
     * Does NOT pause the agent loop.
     */
    data class Narration(
        val message: String,
        val type: String  // "progress" | "found" | "warning" | "done"
    ) : AgentEvent()

    /**
     * Agent is about to perform an irreversible action and needs confirmation.
     * The agent loop pauses until the user approves or denies.
     */
    data class ConfirmationRequired(
        val summary: String,
        val actionPreview: String
    ) : AgentEvent()

    /**
     * The user replied to a question or confirmation.
     * Injected into the agent context when resumeWithUserReply() is called.
     */
    data class UserReplied(
        val reply: String
    ) : AgentEvent()

    /**
     * The agent has decomposed a complex task into subtasks.
     */
    data class TaskDecomposed(
        val steps: List<String>
    ) : AgentEvent()
}

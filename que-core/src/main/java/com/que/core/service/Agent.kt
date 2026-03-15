package com.que.core.service
import com.que.core.model.AgentCheckpoint
import com.que.core.model.AgentEvent
import com.que.core.model.AgentState

import kotlinx.coroutines.flow.SharedFlow
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
     * Flow of agent events for bidirectional communication.
     * Emits UserQuestionAsked, Narration, ConfirmationRequired, etc.
     */
    val events: SharedFlow<AgentEvent>

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
     * Resume the agent loop after it paused for user input.
     * Called when the user answers a question or confirms/denies an action.
     * @param reply The user's reply text (or "yes"/"no" for confirmations).
     */
    fun resumeWithUserReply(reply: String)
    
    /**
     * Creates a checkpoint of the current agent state.
     */
    suspend fun createCheckpoint(): AgentCheckpoint
    
    /**
     * Restores the agent from a checkpoint.
     */
    suspend fun restoreFromCheckpoint(checkpoint: AgentCheckpoint)
}


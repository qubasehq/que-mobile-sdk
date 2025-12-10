package com.que.core

import kotlinx.serialization.Serializable

/**
 * Represents a checkpoint of the agent's state that can be saved and restored.
 */
@Serializable
data class AgentCheckpoint(
    val taskId: String,
    val step: Int,
    val loopState: AgentLoopState,
    val history: List<AgentStepHistory>,
    val memoryMessages: List<Message>,
    val timestamp: Long = System.currentTimeMillis()
)
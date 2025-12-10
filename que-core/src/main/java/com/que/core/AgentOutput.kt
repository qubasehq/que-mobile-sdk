package com.que.core

import kotlinx.serialization.Serializable

/**
 * Structured output from the LLM for agent decision-making.
 * Supports multiple actions per step.
 */
@Serializable
data class AgentOutput(
    val thought: String,
    val nextGoal: String,
    val actions: List<Action>,
    val confidence: Float = 1.0f
)

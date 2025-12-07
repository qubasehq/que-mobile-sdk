package com.que.core

/**
 * Structured output from the LLM for agent decision-making.
 * Supports multiple actions per step.
 */
data class AgentOutput(
    val thought: String,
    val nextGoal: String,
    val actions: List<Action>,
    val confidence: Float = 1.0f
)

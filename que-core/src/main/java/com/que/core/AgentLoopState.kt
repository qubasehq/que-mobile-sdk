package com.que.core

/**
 * Internal state tracking for the agent loop.
 * Tracks progress, failures, and previous outputs.
 */
data class AgentLoopState(
    var nSteps: Int = 0,
    var stopped: Boolean = false,
    var consecutiveFailures: Int = 0,
    var lastModelOutput: AgentOutput? = null,
    var lastResults: List<ActionResult> = emptyList(),
    var planningFailures: Int = 0
)

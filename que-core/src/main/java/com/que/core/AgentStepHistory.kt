package com.que.core

import kotlinx.serialization.Serializable

/**
 * Structured history entry for a single agent step.
 * Records everything that happened in one iteration of the loop.
 */
@Serializable
data class AgentStepHistory(
    val step: Int,
    val modelOutput: AgentOutput?,
    val results: List<ActionResult>,
    val screenState: ScreenSnapshot,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val failureCount: Int = 0,
    val speechLog: List<String> = emptyList(),
    val systemNotes: List<String> = emptyList()
)

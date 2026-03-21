package com.que.core.engine

/**
 * Interface to allow the platform-level history repository 
 * to be injected into the core QueAgent loop.
 */
interface AgentHistoryTracker {
    fun startTask(taskText: String): Long
    fun recordAction(taskId: Long, timestamp: Long, description: String, actionType: String, appName: String, success: Boolean, errorDetail: String)
    fun completeTask(taskId: Long, summary: String, tokenCount: Int)
    fun failTask(taskId: Long, reason: String)
    fun cancelTask(taskId: Long)
    fun extractAndLearn(taskId: Long)
}

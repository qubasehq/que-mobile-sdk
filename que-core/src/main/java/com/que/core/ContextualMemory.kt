package com.que.core

/**
 * Interface for long-term memory storage and retrieval.
 * Allows the agent to remember facts, user preferences, and successful strategies across sessions.
 */
interface ContextualMemory {
    /**
     * Store a value in memory.
     */
    suspend fun remember(key: String, value: String, context: MemoryContext)
    
    /**
     * Recall relevant memories based on a query.
     */
    suspend fun recall(query: String, context: MemoryContext): List<Memory>
    
    /**
     * Forget a specific memory.
     */
    suspend fun forget(key: String)
    
    /**
     * Get a high-level summary of relevant context.
     */
    suspend fun getSummary(context: MemoryContext): String
}

data class MemoryContext(
    val app: String? = null,
    val domain: String? = null, // "shopping", "social", "productivity"
    val timeframe: Long? = null
)

data class Memory(
    val key: String,
    val value: String,
    val timestamp: Long,
    val accessCount: Int,
    val relevance: Float // 0.0 to 1.0
)

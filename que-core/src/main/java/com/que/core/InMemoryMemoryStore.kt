package com.que.core

/**
 * Validates the ContextualMemory interface with an in-memory implementation.
 * In a real app, this would use Room or a Vector Database.
 */
class InMemoryMemoryStore : ContextualMemory {
    
    private val store = mutableListOf<Memory>()
    
    override suspend fun remember(key: String, value: String, context: MemoryContext) {
        // Remove existing if key exists
        forget(key)
        
        store.add(
            Memory(
                key = key,
                value = value,
                timestamp = System.currentTimeMillis(),
                accessCount = 0,
                relevance = 1.0f
            )
        )
    }
    
    override suspend fun recall(query: String, context: MemoryContext): List<Memory> {
        // Simple keyword matching for MVP
        val queryLower = query.lowercase()
        
        return store.filter { memory ->
            val contentMatch = memory.value.lowercase().contains(queryLower) || 
                             memory.key.lowercase().contains(queryLower)
            
            val appMatch = context.app == null || memory.key.contains(context.app) 
            
            contentMatch && appMatch
        }.sortedByDescending { it.timestamp }
         .take(5)
    }
    
    override suspend fun forget(key: String) {
        store.removeAll { it.key == key }
    }
    
    override suspend fun getSummary(context: MemoryContext): String {
        val appMemories = store.filter { 
            context.app != null && it.key.contains(context.app) 
        }
        
        if (appMemories.isEmpty()) return "No previous context for this app."
        
        return "You have ${appMemories.size} memories related to ${context.app}."
    }
}

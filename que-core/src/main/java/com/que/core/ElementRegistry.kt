package com.que.core

/**
 * A shared registry to map Element IDs (used by LLM) to actual UI Elements (used by Executor).
 * This bridges the gap between Perception and Action.
 */
object ElementRegistry {
    private val elements = mutableMapOf<Int, InteractiveElement>()

    fun update(newElements: List<InteractiveElement>) {
        synchronized(this) {
            elements.clear()
            newElements.forEach { elements[it.id] = it }
        }
    }

    fun get(id: Int): InteractiveElement? {
        synchronized(this) {
            return elements[id]
        }
    }

    fun clear() {
        synchronized(this) {
            elements.clear()
        }
    }
}

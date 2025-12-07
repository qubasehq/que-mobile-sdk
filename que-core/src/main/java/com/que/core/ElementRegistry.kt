package com.que.core

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A shared registry to map Element IDs (used by LLM) to actual UI Elements (used by Executor).
 * This bridges the gap between Perception and Action.
 */
object ElementRegistry {
    private val lock = ReentrantReadWriteLock()
    private var elements = mapOf<Int, InteractiveElement>()

    fun update(newElements: List<InteractiveElement>) {
        lock.write {
            elements = newElements.associateBy { it.id }
        }
    }

    fun get(id: Int): InteractiveElement? {
        return lock.read {
            elements[id]
        }
    }

    fun clear() {
        lock.write {
            elements = emptyMap()
        }
    }
}

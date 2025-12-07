package com.que.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple Circuit Breaker to prevent cascading failures.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeout: Long = 30000 // 30 seconds
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }
    
    private var state = State.CLOSED
    private var failures = 0
    private var lastFailureTime = 0L
    private val mutex = Mutex()

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeout) {
                    state = State.HALF_OPEN
                } else {
                    throw RuntimeException("Circuit Breaker is OPEN. Request rejected.")
                }
            }
        }

        try {
            val result = block()
            onSuccess()
            return result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun onSuccess() {
        mutex.withLock {
            failures = 0
            state = State.CLOSED
        }
    }

    private suspend fun onFailure() {
        mutex.withLock {
            failures++
            lastFailureTime = System.currentTimeMillis()
            if (failures >= failureThreshold) {
                state = State.OPEN
            }
        }
    }
}

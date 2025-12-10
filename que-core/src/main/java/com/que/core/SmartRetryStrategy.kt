package com.que.core

// No import needed for ActionResult as it is in the same package

sealed class RetryStrategy {
    object NoRetry : RetryStrategy()
    data class Immediate(val maxAttempts: Int) : RetryStrategy()
    data class ExponentialBackoff(val initialDelay: Long, val maxAttempts: Int) : RetryStrategy()
    data class LinearBackoff(val delay: Long, val maxAttempts: Int) : RetryStrategy()
}

class SmartRetryStrategy {
    fun determineStrategy(error: ActionResult): RetryStrategy {
        val msg = error.message.lowercase()
        return when {
            // Logic/Validation errors - definitely fatal
            msg.contains("invalid parameter") || 
            msg.contains("unsupported action") -> RetryStrategy.NoRetry
            
            // Rate limits - standard backoff
            msg.contains("rate limit") || msg.contains("429") -> {
                RetryStrategy.ExponentialBackoff(initialDelay = 5000, maxAttempts = 3)
            }
            
            // Network/Connection - aggressive backoff
            msg.contains("network") || 
            msg.contains("connection") || 
            msg.contains("socket") ||
            msg.contains("timeout") -> {
                RetryStrategy.ExponentialBackoff(initialDelay = 2000, maxAttempts = 5)
            }
            
            // Element Not Found - THIS WAS THE BUG. 
            // Often caused by animation lag or screen transition. Should retry briefly.
            msg.contains("not found") -> {
                RetryStrategy.LinearBackoff(delay = 1000, maxAttempts = 3)
            }
            
            // Interaction failed (e.g. click didn't register) - fast retry
            !error.success -> {
                RetryStrategy.Immediate(maxAttempts = 2)
            }
            
            else -> RetryStrategy.NoRetry
        }
    }
    
    suspend fun executeWithRetry(
        action: suspend () -> ActionResult,
        strategy: RetryStrategy
    ): ActionResult {
        return when (strategy) {
            is RetryStrategy.NoRetry -> action()
            
            is RetryStrategy.Immediate -> {
                var result = action()
                var attempts = 1
                
                while (!result.success && attempts < strategy.maxAttempts) {
                    result = action()
                    attempts++
                }
                result
            }
            
            is RetryStrategy.ExponentialBackoff -> {
                var result = action()
                var attempts = 1
                var delay = strategy.initialDelay
                
                while (!result.success && attempts < strategy.maxAttempts) {
                    // Log retry?
                    kotlinx.coroutines.delay(delay)
                    result = action()
                    attempts++
                    delay *= 2
                }
                result
            }
            
            is RetryStrategy.LinearBackoff -> {
                var result = action()
                var attempts = 1
                
                while (!result.success && attempts < strategy.maxAttempts) {
                     // Log retry?
                    kotlinx.coroutines.delay(strategy.delay)
                    result = action()
                    attempts++
                }
                result
            }
        }
    }
}

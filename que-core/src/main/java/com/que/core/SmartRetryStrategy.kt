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
        return when {
            // Fatal errors (logic errors) should not be retried blindly
            error.message.contains("not found", ignoreCase = true) -> RetryStrategy.NoRetry
            error.message.contains("invalid", ignoreCase = true) -> RetryStrategy.NoRetry
            
            // Rate limits need exponential backoff
            error.message.contains("rate limit", ignoreCase = true) -> {
                RetryStrategy.ExponentialBackoff(initialDelay = 5000, maxAttempts = 3)
            }
            
            // Network issues need backoff but more attempts
            error.message.contains("network", ignoreCase = true) || 
            error.message.contains("connection", ignoreCase = true) -> {
                RetryStrategy.ExponentialBackoff(initialDelay = 2000, maxAttempts = 5)
            }
            
            // Timeouts can be retried with linear backoff
            error.message.contains("timeout", ignoreCase = true) -> {
                RetryStrategy.LinearBackoff(delay = 3000, maxAttempts = 2)
            }
            
            // Generic retryable failures (e.g. click missed)
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
                    kotlinx.coroutines.delay(strategy.delay)
                    result = action()
                    attempts++
                }
                result
            }
        }
    }
}

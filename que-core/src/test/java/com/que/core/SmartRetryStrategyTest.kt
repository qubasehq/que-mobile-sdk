package com.que.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRetryStrategyTest {

    private val strategy = SmartRetryStrategy()

    @Test
    fun `test determineStrategy returns NoRetry for logical errors`() {
        val result = ActionResult(false, "Invalid parameter: x cannot be negative")
        val retryStrategy = strategy.determineStrategy(result)
        assertTrue(retryStrategy is RetryStrategy.NoRetry)
    }

    @Test
    fun `test determineStrategy returns LinearBackoff for Element Not Found`() {
        val result = ActionResult(false, "Element with ID 123 not found on screen")
        val retryStrategy = strategy.determineStrategy(result)
        
        assertTrue(retryStrategy is RetryStrategy.LinearBackoff)
        val backoff = retryStrategy as RetryStrategy.LinearBackoff
        assertEquals(1000L, backoff.delay)
        assertEquals(3, backoff.maxAttempts)
    }

    @Test
    fun `test determineStrategy returns ExponentialBackoff for Network errors`() {
        val result = ActionResult(false, "Network timeout connecting to server")
        val retryStrategy = strategy.determineStrategy(result)
        
        assertTrue(retryStrategy is RetryStrategy.ExponentialBackoff)
        val exp = retryStrategy as RetryStrategy.ExponentialBackoff
        assertEquals(2000L, exp.initialDelay)
        assertEquals(5, exp.maxAttempts)
    }

    @Test
    fun `test executeWithRetry retries immediately for interaction failure`() = runTest {
        val failureResult = ActionResult(false, "Click failed")
        val successResult = ActionResult(true, "Click success")
        
        var attempts = 0
        val finalResult = strategy.executeWithRetry(
            action = {
                attempts++
                if (attempts < 2) failureResult else successResult
            },
            strategy = RetryStrategy.Immediate(maxAttempts = 3)
        )
        
        assertEquals(2, attempts)
        assertTrue(finalResult.success)
    }
}

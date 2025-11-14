/**
 * ActionRetryHandler - Robust error handling and retry logic for actions
 */

import { ActionResult } from '../types'

/**
 * Retry configuration
 */
export interface RetryConfig {
  maxRetries: number
  retryDelay: number // milliseconds
  exponentialBackoff: boolean
  retryableErrors: string[]
}

/**
 * Error classification
 */
export enum ErrorType {
  TRANSIENT = 'transient', // Can be retried
  PERMANENT = 'permanent', // Cannot be retried
  RATE_LIMIT = 'rate_limit', // Rate limited, needs longer delay
  PERMISSION = 'permission', // Permission denied
  NOT_FOUND = 'not_found', // Resource not found
  INVALID_INPUT = 'invalid_input', // Invalid parameters
  NETWORK = 'network', // Network error
  TIMEOUT = 'timeout', // Operation timed out
}

/**
 * Enhanced action result with retry metadata
 */
export interface EnhancedActionResult extends ActionResult {
  errorType?: ErrorType
  retryable?: boolean
  retryCount?: number
  executionTime?: number
}

/**
 * ActionRetryHandler - Handles retries and error recovery
 */
export class ActionRetryHandler {
  private defaultConfig: RetryConfig = {
    maxRetries: 3,
    retryDelay: 1000,
    exponentialBackoff: true,
    retryableErrors: [
      'timeout',
      'network',
      'service unavailable',
      'temporarily unavailable',
      'rate limit',
    ],
  }

  constructor(private config: Partial<RetryConfig> = {}) {
    this.config = { ...this.defaultConfig, ...config }
  }

  /**
   * Execute an action with retry logic
   */
  async executeWithRetry<T>(
    actionFn: () => Promise<T>,
    actionName: string,
    retryConfig?: Partial<RetryConfig>
  ): Promise<T> {
    const config = { ...this.config, ...retryConfig }
    let lastError: Error | null = null
    let retryCount = 0

    while (retryCount <= config.maxRetries!) {
      try {
        const startTime = Date.now()
        const result = await actionFn()
        const executionTime = Date.now() - startTime

        // Log successful execution
        console.log(`[ActionRetry] ${actionName} succeeded in ${executionTime}ms (attempt ${retryCount + 1})`)

        return result
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error))
        retryCount++

        // Check if error is retryable
        const errorType = this.classifyError(lastError)
        const isRetryable = this.isRetryableError(errorType, lastError.message)

        console.warn(
          `[ActionRetry] ${actionName} failed (attempt ${retryCount}/${config.maxRetries! + 1}): ${lastError.message}`
        )

        // Don't retry if error is not retryable or max retries reached
        if (!isRetryable || retryCount > config.maxRetries!) {
          throw lastError
        }

        // Calculate delay with exponential backoff
        const delay = config.exponentialBackoff!
          ? config.retryDelay! * Math.pow(2, retryCount - 1)
          : config.retryDelay!

        // Add jitter to prevent thundering herd
        const jitter = Math.random() * 200
        const totalDelay = delay + jitter

        console.log(`[ActionRetry] Retrying ${actionName} in ${totalDelay.toFixed(0)}ms...`)

        await this.sleep(totalDelay)
      }
    }

    throw lastError || new Error('Unknown error during retry')
  }

  /**
   * Classify error type
   */
  classifyError(error: Error): ErrorType {
    const message = error.message.toLowerCase()

    if (message.includes('timeout') || message.includes('timed out')) {
      return ErrorType.TIMEOUT
    }

    if (message.includes('network') || message.includes('connection')) {
      return ErrorType.NETWORK
    }

    if (message.includes('rate limit') || message.includes('too many requests')) {
      return ErrorType.RATE_LIMIT
    }

    if (message.includes('permission') || message.includes('unauthorized') || message.includes('forbidden')) {
      return ErrorType.PERMISSION
    }

    if (message.includes('not found') || message.includes('does not exist')) {
      return ErrorType.NOT_FOUND
    }

    if (message.includes('invalid') || message.includes('malformed')) {
      return ErrorType.INVALID_INPUT
    }

    if (
      message.includes('temporarily unavailable') ||
      message.includes('service unavailable') ||
      message.includes('try again')
    ) {
      return ErrorType.TRANSIENT
    }

    return ErrorType.PERMANENT
  }

  /**
   * Check if error is retryable
   */
  isRetryableError(errorType: ErrorType, errorMessage: string): boolean {
    // Permanent errors should not be retried
    const nonRetryableTypes = [ErrorType.PERMISSION, ErrorType.INVALID_INPUT, ErrorType.NOT_FOUND]

    if (nonRetryableTypes.includes(errorType)) {
      return false
    }

    // Check against retryable error patterns
    const lowerMessage = errorMessage.toLowerCase()
    return this.config.retryableErrors!.some(pattern => lowerMessage.includes(pattern.toLowerCase()))
  }

  /**
   * Enhance action result with error metadata
   */
  enhanceResult(result: ActionResult, error?: Error, retryCount: number = 0): EnhancedActionResult {
    if (!error) {
      return {
        ...result,
        retryCount,
      }
    }

    const errorType = this.classifyError(error)
    const retryable = this.isRetryableError(errorType, error.message)

    return {
      ...result,
      errorType,
      retryable,
      retryCount,
      error: result.error || error.message,
    }
  }

  /**
   * Create a user-friendly error message
   */
  formatErrorForUser(error: Error, errorType: ErrorType, actionName: string): string {
    switch (errorType) {
      case ErrorType.TIMEOUT:
        return `Action '${actionName}' timed out. The operation took too long to complete. Try again or break it into smaller steps.`

      case ErrorType.NETWORK:
        return `Network error during '${actionName}'. Check your internet connection and try again.`

      case ErrorType.RATE_LIMIT:
        return `Rate limit exceeded for '${actionName}'. Please wait a moment before trying again.`

      case ErrorType.PERMISSION:
        return `Permission denied for '${actionName}'. This action requires additional permissions that are not granted.`

      case ErrorType.NOT_FOUND:
        return `Resource not found for '${actionName}'. The requested item may have been removed or doesn't exist.`

      case ErrorType.INVALID_INPUT:
        return `Invalid input for '${actionName}': ${error.message}. Please check your parameters and try again.`

      case ErrorType.TRANSIENT:
        return `Temporary error during '${actionName}': ${error.message}. This should resolve itself shortly.`

      case ErrorType.PERMANENT:
      default:
        return `Error during '${actionName}': ${error.message}`
    }
  }

  /**
   * Sleep for specified milliseconds
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
}

/**
 * Circuit breaker pattern for preventing cascading failures
 */
export class CircuitBreaker {
  private failureCount: number = 0
  private lastFailureTime: number = 0
  private state: 'closed' | 'open' | 'half-open' = 'closed'

  constructor(
    private threshold: number = 5,
    _timeout: number = 60000, // 1 minute (reserved for future use)
    private resetTimeout: number = 30000 // 30 seconds
  ) {}

  /**
   * Execute function with circuit breaker protection
   */
  async execute<T>(fn: () => Promise<T>): Promise<T> {
    if (this.state === 'open') {
      const timeSinceLastFailure = Date.now() - this.lastFailureTime

      if (timeSinceLastFailure > this.resetTimeout) {
        console.log('[CircuitBreaker] Attempting to close circuit (half-open state)')
        this.state = 'half-open'
      } else {
        throw new Error(
          `Circuit breaker is open. Too many failures. Try again in ${Math.ceil((this.resetTimeout - timeSinceLastFailure) / 1000)}s`
        )
      }
    }

    try {
      const result = await fn()

      // Success - reset failure count
      if (this.state === 'half-open') {
        console.log('[CircuitBreaker] Circuit closed after successful execution')
        this.state = 'closed'
        this.failureCount = 0
      }

      return result
    } catch (error) {
      this.failureCount++
      this.lastFailureTime = Date.now()

      console.warn(`[CircuitBreaker] Failure ${this.failureCount}/${this.threshold}`)

      if (this.failureCount >= this.threshold) {
        console.error('[CircuitBreaker] Circuit opened due to too many failures')
        this.state = 'open'
      }

      throw error
    }
  }

  /**
   * Get current circuit breaker state
   */
  getState(): { state: string; failureCount: number; lastFailureTime: number } {
    return {
      state: this.state,
      failureCount: this.failureCount,
      lastFailureTime: this.lastFailureTime,
    }
  }

  /**
   * Manually reset the circuit breaker
   */
  reset(): void {
    this.state = 'closed'
    this.failureCount = 0
    this.lastFailureTime = 0
  }
}

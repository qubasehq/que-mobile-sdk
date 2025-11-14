/**
 * Error type definitions for QUE Mobile SDK
 */

// ============================================================================
// Error Category Enum
// ============================================================================

export enum ErrorCategory {
  /** Errors related to native module or Accessibility Service */
  NATIVE_MODULE = 'native_module',
  /** Errors related to LLM communication or parsing */
  LLM = 'llm',
  /** Errors during action execution */
  ACTION_EXECUTION = 'action_execution',
  /** System-level errors (memory, max steps, etc.) */
  SYSTEM = 'system',
}

// ============================================================================
// QueError Class
// ============================================================================

export class QueError extends Error {
  /** Error category for classification */
  public readonly category: ErrorCategory

  /** Whether this error is recoverable */
  public readonly recoverable: boolean

  /** Additional error details */
  public readonly details?: any

  /** Timestamp when error occurred */
  public readonly timestamp: number

  constructor(
    message: string,
    category: ErrorCategory,
    recoverable: boolean = false,
    details?: any
  ) {
    super(message)
    this.name = 'QueError'
    this.category = category
    this.recoverable = recoverable
    this.details = details
    this.timestamp = Date.now()

    // Maintains proper stack trace for where error was thrown (V8 only)
    if (typeof (Error as any).captureStackTrace === 'function') {
      (Error as any).captureStackTrace(this, QueError)
    }
  }

  /**
   * Convert error to JSON for logging/serialization
   */
  toJSON() {
    return {
      name: this.name,
      message: this.message,
      category: this.category,
      recoverable: this.recoverable,
      details: this.details,
      timestamp: this.timestamp,
      stack: this.stack,
    }
  }

  /**
   * Get user-friendly error message
   */
  getUserMessage(): string {
    switch (this.category) {
      case ErrorCategory.NATIVE_MODULE:
        return `Native module error: ${this.message}. Please ensure Accessibility Service is enabled.`
      case ErrorCategory.LLM:
        return `AI communication error: ${this.message}. Please check your API key and network connection.`
      case ErrorCategory.ACTION_EXECUTION:
        return `Action failed: ${this.message}. The agent will attempt to recover.`
      case ErrorCategory.SYSTEM:
        return `System error: ${this.message}.`
      default:
        return this.message
    }
  }
}

// ============================================================================
// Specific Error Classes
// ============================================================================

/**
 * Error thrown when native module is not available or not configured
 */
export class NativeModuleError extends QueError {
  constructor(message: string, details?: any) {
    super(message, ErrorCategory.NATIVE_MODULE, false, details)
    this.name = 'NativeModuleError'
  }
}

/**
 * Error thrown when Accessibility Service is not enabled
 */
export class AccessibilityServiceError extends QueError {
  constructor(message: string = 'Accessibility Service is not enabled', details?: any) {
    super(message, ErrorCategory.NATIVE_MODULE, false, details)
    this.name = 'AccessibilityServiceError'
  }
}

/**
 * Error thrown when LLM API call fails
 */
export class LLMError extends QueError {
  constructor(message: string, recoverable: boolean = true, details?: any) {
    super(message, ErrorCategory.LLM, recoverable, details)
    this.name = 'LLMError'
  }
}

/**
 * Error thrown when LLM response cannot be parsed
 */
export class LLMParseError extends QueError {
  constructor(message: string, response?: string) {
    super(message, ErrorCategory.LLM, true, { response })
    this.name = 'LLMParseError'
  }
}

/**
 * Error thrown when action execution fails
 */
export class ActionExecutionError extends QueError {
  constructor(message: string, actionType: string, recoverable: boolean = true, details?: any) {
    super(message, ErrorCategory.ACTION_EXECUTION, recoverable, { actionType, ...details })
    this.name = 'ActionExecutionError'
  }
}

/**
 * Error thrown when element is not found
 */
export class ElementNotFoundError extends QueError {
  constructor(elementId: number, details?: any) {
    super(
      `Element with ID ${elementId} not found in current screen state`,
      ErrorCategory.ACTION_EXECUTION,
      true,
      { elementId, ...details }
    )
    this.name = 'ElementNotFoundError'
  }
}

/**
 * Error thrown when max steps is reached
 */
export class MaxStepsError extends QueError {
  constructor(maxSteps: number) {
    super(
      `Maximum steps (${maxSteps}) reached without completion`,
      ErrorCategory.SYSTEM,
      false,
      { maxSteps }
    )
    this.name = 'MaxStepsError'
  }
}

/**
 * Error thrown when consecutive failures exceed threshold
 */
export class MaxFailuresError extends QueError {
  constructor(maxFailures: number) {
    super(
      `Maximum consecutive failures (${maxFailures}) exceeded`,
      ErrorCategory.SYSTEM,
      false,
      { maxFailures }
    )
    this.name = 'MaxFailuresError'
  }
}

/**
 * Error thrown when file operation fails
 */
export class FileSystemError extends QueError {
  constructor(message: string, operation: string, fileName?: string) {
    super(message, ErrorCategory.ACTION_EXECUTION, true, { operation, fileName })
    this.name = 'FileSystemError'
  }
}

/**
 * Error thrown when voice operation fails
 */
export class VoiceError extends QueError {
  constructor(message: string, operation: 'speak' | 'listen', details?: any) {
    super(message, ErrorCategory.ACTION_EXECUTION, true, { operation, ...details })
    this.name = 'VoiceError'
  }
}

// ============================================================================
// Error Helper Functions
// ============================================================================

/**
 * Check if error is a QueError
 */
export function isQueError(error: any): error is QueError {
  return error instanceof QueError
}

/**
 * Check if error is recoverable
 */
export function isRecoverableError(error: any): boolean {
  return isQueError(error) && error.recoverable
}

/**
 * Get error category from any error
 */
export function getErrorCategory(error: any): ErrorCategory {
  if (isQueError(error)) {
    return error.category
  }
  return ErrorCategory.SYSTEM
}

/**
 * Convert any error to QueError
 */
export function toQueError(error: any): QueError {
  if (isQueError(error)) {
    return error
  }

  if (error instanceof Error) {
    return new QueError(error.message, ErrorCategory.SYSTEM, false, {
      originalError: error.name,
      stack: error.stack,
    })
  }

  return new QueError(String(error), ErrorCategory.SYSTEM, false)
}

/**
 * Format error for LLM context
 */
export function formatErrorForLLM(error: any): string {
  if (isQueError(error)) {
    const parts = [
      `Error: ${error.message}`,
      `Category: ${error.category}`,
      `Recoverable: ${error.recoverable}`,
    ]

    if (error.details) {
      parts.push(`Details: ${JSON.stringify(error.details, null, 2)}`)
    }

    return parts.join('\n')
  }

  if (error instanceof Error) {
    return `Error: ${error.message}`
  }

  return `Error: ${String(error)}`
}

/**
 * Create error recovery message for LLM
 */
export function createRecoveryMessage(error: QueError, attemptNumber: number): string {
  const messages = {
    [ErrorCategory.NATIVE_MODULE]: `The native module encountered an error. This may indicate the Accessibility Service is not properly configured. Attempt ${attemptNumber}.`,
    [ErrorCategory.LLM]: `The previous response could not be parsed. Please ensure your response is valid JSON matching the expected schema. Attempt ${attemptNumber}.`,
    [ErrorCategory.ACTION_EXECUTION]: `The action failed: ${error.message}. Please try a different approach or verify the element exists. Attempt ${attemptNumber}.`,
    [ErrorCategory.SYSTEM]: `A system error occurred: ${error.message}. Attempt ${attemptNumber}.`,
  }

  return messages[error.category] || `An error occurred: ${error.message}. Attempt ${attemptNumber}.`
}

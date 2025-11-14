/**
 * useAgent Hook - React hook for managing Agent lifecycle
 * 
 * Provides a simple interface for executing AI-powered automation tasks
 * with state management for running status, results, errors, and history.
 * 
 * Requirements: 1.1, 1.2, 1.3
 */

import { useState, useRef, useCallback } from 'react'
import { Agent } from '../core/Agent'
import { AgentConfig, AgentResult, AgentHistory } from '../types'

// ============================================================================
// Hook Return Type
// ============================================================================

export interface UseAgentReturn {
  /** Execute a task with the agent */
  execute: (task: string, maxSteps?: number) => Promise<void>
  /** Whether agent is currently running */
  isRunning: boolean
  /** Result from last execution (null if not completed) */
  result: AgentResult | null
  /** Error from last execution (null if no error) */
  error: string | null
  /** Stop the currently running agent */
  stop: () => void
  /** Complete execution history */
  history: AgentHistory[]
  /** Current agent instance (null if not running) */
  agent: Agent | null
}

// ============================================================================
// useAgent Hook
// ============================================================================

/**
 * React hook for managing Agent execution
 * 
 * @param config - Agent configuration with API key and settings
 * @returns Object with execute function, state, and controls
 * 
 * @example
 * ```tsx
 * const { execute, isRunning, result, error } = useAgent({
 *   apiKey: 'YOUR_GEMINI_API_KEY',
 *   maxSteps: 50,
 *   debugMode: true,
 * })
 * 
 * // Execute a task
 * await execute('Open Instagram and like the first post')
 * 
 * // Check result
 * if (result?.success) {
 *   console.log('Task completed:', result.message)
 * }
 * ```
 */
export function useAgent(config: AgentConfig): UseAgentReturn {
  // State
  const [isRunning, setIsRunning] = useState(false)
  const [result, setResult] = useState<AgentResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [history, setHistory] = useState<AgentHistory[]>([])

  // Refs
  const agentRef = useRef<Agent | null>(null)

  /**
   * Execute a task with the agent
   */
  const execute = useCallback(
    async (task: string, maxSteps?: number): Promise<void> => {
      // Prevent concurrent executions
      if (isRunning) {
        throw new Error('Agent is already running. Stop the current execution first.')
      }

      // Reset state
      setIsRunning(true)
      setResult(null)
      setError(null)
      setHistory([])

      try {
        // Create new agent instance with merged config
        const agentConfig: AgentConfig = {
          ...config,
          maxSteps: maxSteps || config.maxSteps,
          onStep: (step) => {
            // Update history on each step
            if (agentRef.current) {
              setHistory(agentRef.current.getHistory())
            }
            // Call user's onStep callback if provided
            if (config.onStep) {
              config.onStep(step)
            }
          },
          onComplete: (agentResult) => {
            // Update result state
            setResult(agentResult)
            // Call user's onComplete callback if provided
            if (config.onComplete) {
              config.onComplete(agentResult)
            }
          },
          onError: (err) => {
            // Update error state
            setError(err.message)
            // Call user's onError callback if provided
            if (config.onError) {
              config.onError(err)
            }
          },
        }

        agentRef.current = new Agent(agentConfig)

        // Run the agent
        const agentResult = await agentRef.current.run(task, maxSteps)

        // Update final state
        setResult(agentResult)
        setHistory(agentRef.current.getHistory())

        // Clear error if successful
        if (agentResult.success) {
          setError(null)
        } else {
          setError(agentResult.message)
        }
      } catch (err) {
        // Handle execution errors
        const errorMessage = err instanceof Error ? err.message : String(err)
        setError(errorMessage)

        // Create error result
        const errorResult: AgentResult = {
          success: false,
          message: errorMessage,
          steps: agentRef.current?.getState().nSteps || 0,
          history: agentRef.current?.getHistory() || [],
          files: [],
        }
        setResult(errorResult)

        // Update history
        if (agentRef.current) {
          setHistory(agentRef.current.getHistory())
        }
      } finally {
        // Always set running to false when done
        setIsRunning(false)
      }
    },
    [config, isRunning]
  )

  /**
   * Stop the currently running agent
   */
  const stop = useCallback(() => {
    if (agentRef.current && isRunning) {
      agentRef.current.stop()
    }
  }, [isRunning])

  return {
    execute,
    isRunning,
    result,
    error,
    stop,
    history,
    agent: agentRef.current,
  }
}


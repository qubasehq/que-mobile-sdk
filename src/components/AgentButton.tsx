/**
 * AgentButton - Quick Action Button Component
 * 
 * A button component that executes an agent task with built-in loading state
 * and result display. Provides a simple way to trigger automation tasks.
 * 
 * Requirements: 1.1
 */

import React, { useState } from 'react'
import {
  TouchableOpacity,
  Text,
  View,
  ActivityIndicator,
  StyleSheet,
  ViewStyle,
  TextStyle,
} from 'react-native'
import { useAgent } from '../hooks/useAgent'
import { useAgentContext } from './AgentProvider'
import { AgentConfig } from '../types'

// ============================================================================
// Component Props
// ============================================================================

export interface AgentButtonProps {
  /** Task to execute when button is pressed */
  task: string
  /** Button label (defaults to task) */
  label?: string
  /** Agent configuration (optional, uses context if available) */
  config?: AgentConfig
  /** Maximum steps for this task (optional) */
  maxSteps?: number
  /** Callback when task completes */
  onComplete?: (success: boolean, message: string) => void
  /** Custom button style */
  style?: ViewStyle
  /** Custom text style */
  textStyle?: TextStyle
  /** Disabled state */
  disabled?: boolean
}

// ============================================================================
// AgentButton Component
// ============================================================================

/**
 * Button component that executes an agent task with loading and result display
 * 
 * @example
 * ```tsx
 * // With AgentProvider
 * <AgentButton task="Open Instagram and like first post" />
 * 
 * // With inline config
 * <AgentButton
 *   task="Search for coffee shops nearby"
 *   config={{ apiKey: 'YOUR_KEY' }}
 *   onComplete={(success, message) => console.log(message)}
 * />
 * ```
 */
export function AgentButton({
  task,
  label,
  config: propsConfig,
  maxSteps,
  onComplete,
  style,
  textStyle,
  disabled = false,
}: AgentButtonProps): React.ReactElement {
  // Try to get config from context, fall back to props
  let contextConfig: AgentConfig | null = null
  try {
    const context = useAgentContext()
    contextConfig = context.config
  } catch {
    // Not in AgentProvider, will use props config
  }

  const config = propsConfig || contextConfig

  if (!config) {
    throw new Error(
      'AgentButton requires either a config prop or to be used within an AgentProvider'
    )
  }

  // State for result display
  const [showResult, setShowResult] = useState(false)
  const [resultMessage, setResultMessage] = useState('')
  const [resultSuccess, setResultSuccess] = useState(false)

  // Use agent hook
  const { execute, isRunning, result, error } = useAgent(config)

  // Handle button press
  const handlePress = async () => {
    setShowResult(false)
    setResultMessage('')

    try {
      await execute(task, maxSteps)

      // Show result after execution
      if (result) {
        setResultSuccess(result.success)
        setResultMessage(result.message)
        setShowResult(true)

        // Call onComplete callback
        if (onComplete) {
          onComplete(result.success, result.message)
        }

        // Auto-hide result after 5 seconds
        setTimeout(() => {
          setShowResult(false)
        }, 5000)
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : String(err)
      setResultSuccess(false)
      setResultMessage(errorMessage)
      setShowResult(true)

      // Call onComplete callback with error
      if (onComplete) {
        onComplete(false, errorMessage)
      }

      // Auto-hide error after 5 seconds
      setTimeout(() => {
        setShowResult(false)
      }, 5000)
    }
  }

  const isDisabled = disabled || isRunning

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={[
          styles.button,
          isDisabled && styles.buttonDisabled,
          style,
        ]}
        onPress={handlePress}
        disabled={isDisabled}
        activeOpacity={0.7}
      >
        {isRunning ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator color="#FFFFFF" size="small" />
            <Text style={[styles.buttonText, styles.loadingText, textStyle]}>
              Running...
            </Text>
          </View>
        ) : (
          <Text style={[styles.buttonText, textStyle]}>
            {label || task}
          </Text>
        )}
      </TouchableOpacity>

      {showResult && (
        <View
          style={[
            styles.resultContainer,
            resultSuccess ? styles.resultSuccess : styles.resultError,
          ]}
        >
          <Text style={styles.resultIcon}>
            {resultSuccess ? '✓' : '✗'}
          </Text>
          <Text style={styles.resultText} numberOfLines={2}>
            {resultMessage}
          </Text>
        </View>
      )}

      {error && !showResult && (
        <View style={[styles.resultContainer, styles.resultError]}>
          <Text style={styles.resultIcon}>✗</Text>
          <Text style={styles.resultText} numberOfLines={2}>
            {error}
          </Text>
        </View>
      )}
    </View>
  )
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    width: '100%',
  },
  button: {
    backgroundColor: '#007AFF',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  buttonDisabled: {
    backgroundColor: '#CCCCCC',
    opacity: 0.6,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  loadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  loadingText: {
    marginLeft: 8,
  },
  resultContainer: {
    marginTop: 12,
    padding: 12,
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  resultSuccess: {
    backgroundColor: '#E8F5E9',
    borderWidth: 1,
    borderColor: '#4CAF50',
  },
  resultError: {
    backgroundColor: '#FFEBEE',
    borderWidth: 1,
    borderColor: '#F44336',
  },
  resultIcon: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  resultText: {
    flex: 1,
    fontSize: 14,
    color: '#333333',
  },
})

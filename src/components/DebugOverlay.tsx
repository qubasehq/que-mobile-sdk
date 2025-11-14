/**
 * DebugOverlay - Visual Debug Information Component
 * 
 * Displays real-time debug information about agent execution including
 * current step, last action, element count, and LLM reasoning.
 * Only renders when debugMode is enabled.
 * 
 * Requirements: 9.1, 9.2, 9.3, 9.4
 */

import React from 'react'
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  ViewStyle,
} from 'react-native'
import { AgentStep, AgentState } from '../types'

// ============================================================================
// Component Props
// ============================================================================

export interface DebugOverlayProps {
  /** Whether debug mode is enabled (component only renders if true) */
  debugMode: boolean
  /** Current agent state */
  state?: AgentState
  /** Current step number */
  stepNumber?: number
  /** Maximum steps allowed */
  maxSteps?: number
  /** Last step information */
  lastStep?: AgentStep
  /** Custom container style */
  style?: ViewStyle
}

// ============================================================================
// DebugOverlay Component
// ============================================================================

/**
 * Overlay component that displays debug information during agent execution
 * 
 * @example
 * ```tsx
 * <DebugOverlay
 *   debugMode={true}
 *   state={agentState}
 *   stepNumber={5}
 *   maxSteps={50}
 *   lastStep={lastStepInfo}
 * />
 * ```
 */
export function DebugOverlay({
  debugMode,
  state,
  stepNumber,
  maxSteps,
  lastStep,
  style,
}: DebugOverlayProps): React.ReactElement | null {
  // Only render if debug mode is enabled
  if (!debugMode) {
    return null
  }

  // Extract information from state and last step
  const currentStep = stepNumber || state?.nSteps || 0
  const maxStepsValue = maxSteps || 100
  const consecutiveFailures = state?.consecutiveFailures || 0
  const isStopped = state?.stopped || false

  // Extract last action information
  const lastAction = lastStep?.modelOutput?.actions?.[0]
  const lastActionType = lastAction?.type || 'none'
  
  // Extract element count from screen state
  const elementCount = lastStep?.screenState?.elementMap?.size || 0
  
  // Extract LLM reasoning (truncated)
  const reasoning = lastStep?.modelOutput?.nextGoal || 'Waiting for first step...'
  const truncatedReasoning = reasoning.length > 100 
    ? reasoning.substring(0, 100) + '...' 
    : reasoning

  // Extract evaluation
  const evaluation = lastStep?.modelOutput?.evaluationPreviousGoal || 'N/A'
  const truncatedEvaluation = evaluation.length > 80
    ? evaluation.substring(0, 80) + '...'
    : evaluation

  // Extract memory
  const memory = lastStep?.modelOutput?.memory || 'N/A'
  const truncatedMemory = memory.length > 80
    ? memory.substring(0, 80) + '...'
    : memory

  // Calculate progress percentage
  const progressPercentage = Math.round((currentStep / maxStepsValue) * 100)

  return (
    <View style={[styles.container, style]}>
      <View style={styles.header}>
        <Text style={styles.headerText}>🐛 Debug Mode</Text>
        {isStopped && (
          <View style={styles.stoppedBadge}>
            <Text style={styles.stoppedText}>STOPPED</Text>
          </View>
        )}
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {/* Step Progress */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Progress</Text>
          <View style={styles.progressContainer}>
            <Text style={styles.stepText}>
              Step {currentStep} / {maxStepsValue}
            </Text>
            <Text style={styles.percentageText}>({progressPercentage}%)</Text>
          </View>
          <View style={styles.progressBar}>
            <View 
              style={[
                styles.progressFill, 
                { width: `${progressPercentage}%` }
              ]} 
            />
          </View>
        </View>

        {/* Failures */}
        {consecutiveFailures > 0 && (
          <View style={[styles.section, styles.warningSection]}>
            <Text style={styles.sectionTitle}>⚠️ Failures</Text>
            <Text style={styles.valueText}>
              Consecutive: {consecutiveFailures}
            </Text>
          </View>
        )}

        {/* Last Action */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Last Action</Text>
          <Text style={styles.valueText}>{lastActionType}</Text>
          {lastStep?.actionResults && lastStep.actionResults.length > 0 && (
            <Text style={styles.subText}>
              {lastStep.actionResults[0].error 
                ? `❌ ${lastStep.actionResults[0].error}` 
                : '✓ Success'}
            </Text>
          )}
        </View>

        {/* Screen State */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Screen State</Text>
          <Text style={styles.valueText}>
            Elements: {elementCount}
          </Text>
          {lastStep?.screenState && (
            <>
              <Text style={styles.subText}>
                Activity: {lastStep.screenState.activityName || 'Unknown'}
              </Text>
              <Text style={styles.subText}>
                Keyboard: {lastStep.screenState.isKeyboardOpen ? 'Open' : 'Closed'}
              </Text>
              {lastStep.screenState.scrollDown > 0 && (
                <Text style={styles.subText}>
                  ↓ Can scroll down ({lastStep.screenState.scrollDown}px)
                </Text>
              )}
              {lastStep.screenState.scrollUp > 0 && (
                <Text style={styles.subText}>
                  ↑ Can scroll up ({lastStep.screenState.scrollUp}px)
                </Text>
              )}
            </>
          )}
        </View>

        {/* LLM Reasoning */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Next Goal</Text>
          <Text style={styles.reasoningText}>{truncatedReasoning}</Text>
        </View>

        {/* Evaluation */}
        {evaluation !== 'N/A' && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Evaluation</Text>
            <Text style={styles.subText}>{truncatedEvaluation}</Text>
          </View>
        )}

        {/* Memory */}
        {memory !== 'N/A' && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Memory</Text>
            <Text style={styles.subText}>{truncatedMemory}</Text>
          </View>
        )}

        {/* Action Count */}
        {lastStep?.modelOutput?.actions && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Actions Planned</Text>
            <Text style={styles.valueText}>
              {lastStep.modelOutput.actions.length} action(s)
            </Text>
          </View>
        )}
      </ScrollView>
    </View>
  )
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 40,
    right: 10,
    width: 280,
    maxHeight: '80%',
    backgroundColor: 'rgba(0, 0, 0, 0.9)',
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#00FF00',
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  header: {
    backgroundColor: '#00FF00',
    paddingVertical: 8,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerText: {
    color: '#000000',
    fontSize: 14,
    fontWeight: 'bold',
  },
  stoppedBadge: {
    backgroundColor: '#FF0000',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  stoppedText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: 'bold',
  },
  content: {
    padding: 12,
  },
  section: {
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.1)',
  },
  warningSection: {
    backgroundColor: 'rgba(255, 165, 0, 0.1)',
    padding: 8,
    borderRadius: 6,
    borderBottomWidth: 0,
  },
  sectionTitle: {
    color: '#00FF00',
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  valueText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  subText: {
    color: '#CCCCCC',
    fontSize: 11,
    marginTop: 2,
  },
  reasoningText: {
    color: '#FFFFFF',
    fontSize: 12,
    lineHeight: 16,
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  stepText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  percentageText: {
    color: '#CCCCCC',
    fontSize: 12,
  },
  progressBar: {
    height: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#00FF00',
    borderRadius: 3,
  },
})

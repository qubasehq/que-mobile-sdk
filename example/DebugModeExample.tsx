/**
 * Debug Mode Example
 * 
 * Demonstrates the visual debug feedback features including:
 * - Screen flash on state changes
 * - Tap indicators on tap actions
 * - Element bounding boxes
 * - Element labels with IDs
 */

import React, { useState } from 'react'
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  ScrollView,
} from 'react-native'
import {
  useAgent,
  DebugOverlay,
  DebugVisualFeedback,
  AgentStep,
} from 'que-mobile-sdk'

export default function DebugModeExample() {
  const [currentStep, setCurrentStep] = useState<AgentStep | null>(null)
  const [showBoundingBoxes, setShowBoundingBoxes] = useState(true)
  const [showElementLabels, setShowElementLabels] = useState(true)

  // Initialize agent with debug mode enabled
  const { execute, isRunning, result, error, stop } = useAgent({
    apiKey: process.env.GEMINI_API_KEY || 'your-api-key-here',
    maxSteps: 50,
    debugMode: true,
    onStep: (step) => {
      // Update current step for debug visualization
      setCurrentStep(step)
    },
  })

  const handleRunTask = async (task: string) => {
    setCurrentStep(null)
    await execute(task)
  }

  return (
    <SafeAreaView style={styles.container}>
      {/* Debug Visual Feedback Overlay */}
      <DebugVisualFeedback
        debugMode={true}
        screenState={currentStep?.screenState}
        lastTapCoordinates={currentStep?.lastTapCoordinates}
        triggerFlash={currentStep?.triggerFlash}
        showBoundingBoxes={showBoundingBoxes}
        showElementLabels={showElementLabels}
      />

      {/* Debug Overlay */}
      <DebugOverlay
        debugMode={true}
        state={{
          nSteps: currentStep?.stepNumber || 0,
          stopped: false,
          consecutiveFailures: 0,
          lastModelOutput: currentStep?.modelOutput || null,
          lastResult: currentStep?.actionResults || null,
        }}
        stepNumber={currentStep?.stepNumber}
        maxSteps={currentStep?.maxSteps}
        lastStep={currentStep || undefined}
      />

      {/* Main Content */}
      <ScrollView style={styles.content}>
        <Text style={styles.title}>Debug Mode Example</Text>
        <Text style={styles.subtitle}>
          Visual feedback for agent execution
        </Text>

        {/* Debug Controls */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Debug Controls</Text>
          
          <TouchableOpacity
            style={styles.toggleButton}
            onPress={() => setShowBoundingBoxes(!showBoundingBoxes)}
          >
            <Text style={styles.toggleButtonText}>
              {showBoundingBoxes ? '✓' : '○'} Show Bounding Boxes
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.toggleButton}
            onPress={() => setShowElementLabels(!showElementLabels)}
          >
            <Text style={styles.toggleButtonText}>
              {showElementLabels ? '✓' : '○'} Show Element Labels
            </Text>
          </TouchableOpacity>
        </View>

        {/* Example Tasks */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Example Tasks</Text>
          
          <TouchableOpacity
            style={[styles.taskButton, isRunning && styles.taskButtonDisabled]}
            onPress={() => handleRunTask('Open Settings app')}
            disabled={isRunning}
          >
            <Text style={styles.taskButtonText}>
              Open Settings
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.taskButton, isRunning && styles.taskButtonDisabled]}
            onPress={() => handleRunTask('Search Google for React Native')}
            disabled={isRunning}
          >
            <Text style={styles.taskButtonText}>
              Search Google
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.taskButton, isRunning && styles.taskButtonDisabled]}
            onPress={() => handleRunTask('Open app drawer and scroll down')}
            disabled={isRunning}
          >
            <Text style={styles.taskButtonText}>
              Navigate App Drawer
            </Text>
          </TouchableOpacity>

          {isRunning && (
            <TouchableOpacity
              style={[styles.taskButton, styles.stopButton]}
              onPress={stop}
            >
              <Text style={styles.taskButtonText}>
                Stop Agent
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Debug Features Info */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Debug Features</Text>
          
          <View style={styles.featureItem}>
            <Text style={styles.featureIcon}>⚡</Text>
            <View style={styles.featureContent}>
              <Text style={styles.featureTitle}>Screen Flash</Text>
              <Text style={styles.featureDescription}>
                White border flashes when screen state changes
              </Text>
            </View>
          </View>

          <View style={styles.featureItem}>
            <Text style={styles.featureIcon}>🎯</Text>
            <View style={styles.featureContent}>
              <Text style={styles.featureTitle}>Tap Indicator</Text>
              <Text style={styles.featureDescription}>
                Red circle appears at tap locations
              </Text>
            </View>
          </View>

          <View style={styles.featureItem}>
            <Text style={styles.featureIcon}>📦</Text>
            <View style={styles.featureContent}>
              <Text style={styles.featureTitle}>Bounding Boxes</Text>
              <Text style={styles.featureDescription}>
                Green for clickable, yellow for text elements
              </Text>
            </View>
          </View>

          <View style={styles.featureItem}>
            <Text style={styles.featureIcon}>🏷️</Text>
            <View style={styles.featureContent}>
              <Text style={styles.featureTitle}>Element Labels</Text>
              <Text style={styles.featureDescription}>
                Shows element IDs for reference
              </Text>
            </View>
          </View>
        </View>

        {/* Status */}
        {isRunning && (
          <View style={styles.statusSection}>
            <Text style={styles.statusText}>
              🤖 Agent is running...
            </Text>
          </View>
        )}

        {result && (
          <View style={[styles.statusSection, result.success ? styles.successSection : styles.errorSection]}>
            <Text style={styles.statusText}>
              {result.success ? '✓' : '✗'} {result.message}
            </Text>
            <Text style={styles.statusSubtext}>
              Completed in {result.steps} steps
            </Text>
          </View>
        )}

        {error && (
          <View style={[styles.statusSection, styles.errorSection]}>
            <Text style={styles.statusText}>
              ✗ Error: {error}
            </Text>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
  },
  content: {
    flex: 1,
    padding: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#CCCCCC',
    marginBottom: 24,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#00FF00',
    marginBottom: 12,
  },
  toggleButton: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  toggleButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  taskButton: {
    backgroundColor: '#00FF00',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    alignItems: 'center',
  },
  taskButtonDisabled: {
    backgroundColor: '#666666',
    opacity: 0.5,
  },
  stopButton: {
    backgroundColor: '#FF0000',
  },
  taskButtonText: {
    color: '#000000',
    fontSize: 16,
    fontWeight: 'bold',
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  featureIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  featureContent: {
    flex: 1,
  },
  featureTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 4,
  },
  featureDescription: {
    fontSize: 14,
    color: '#CCCCCC',
  },
  statusSection: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
  },
  successSection: {
    backgroundColor: 'rgba(0, 255, 0, 0.1)',
    borderWidth: 1,
    borderColor: '#00FF00',
  },
  errorSection: {
    backgroundColor: 'rgba(255, 0, 0, 0.1)',
    borderWidth: 1,
    borderColor: '#FF0000',
  },
  statusText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  statusSubtext: {
    color: '#CCCCCC',
    fontSize: 12,
  },
})

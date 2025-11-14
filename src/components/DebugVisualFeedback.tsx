/**
 * DebugVisualFeedback - Visual Debug Feedback Component
 * 
 * Provides visual feedback for debug mode including:
 * - Screen flash (white border) on state changes
 * - Tap indicator (red circle) on tap actions
 * - Element bounding boxes (green for clickable, yellow for text)
 * - Element labels with IDs
 * 
 * Requirements: 9.1, 9.2
 */

import React, { useEffect, useState, useRef } from 'react'
import {
  View,
  Text,
  StyleSheet,
  Animated,
  Dimensions,
  ViewStyle,
} from 'react-native'
import { ScreenState, Element } from '../types'

// ============================================================================
// Component Props
// ============================================================================

export interface DebugVisualFeedbackProps {
  /** Whether debug mode is enabled (component only renders if true) */
  debugMode: boolean
  /** Current screen state with elements */
  screenState?: ScreenState
  /** Last tap coordinates for tap indicator */
  lastTapCoordinates?: { x: number; y: number }
  /** Trigger screen flash animation */
  triggerFlash?: boolean
  /** Show element bounding boxes */
  showBoundingBoxes?: boolean
  /** Show element labels with IDs */
  showElementLabels?: boolean
  /** Custom container style */
  style?: ViewStyle
}

// ============================================================================
// DebugVisualFeedback Component
// ============================================================================

/**
 * Component that provides visual debug feedback during agent execution
 * 
 * @example
 * ```tsx
 * <DebugVisualFeedback
 *   debugMode={true}
 *   screenState={currentScreenState}
 *   lastTapCoordinates={{ x: 100, y: 200 }}
 *   triggerFlash={true}
 *   showBoundingBoxes={true}
 *   showElementLabels={true}
 * />
 * ```
 */
export function DebugVisualFeedback({
  debugMode,
  screenState,
  lastTapCoordinates,
  triggerFlash = false,
  showBoundingBoxes = true,
  showElementLabels = true,
  style,
}: DebugVisualFeedbackProps): React.ReactElement | null {
  // Animation values
  const flashOpacity = useRef(new Animated.Value(0)).current
  const tapScale = useRef(new Animated.Value(0)).current
  const tapOpacity = useRef(new Animated.Value(0)).current

  // State for tap indicator
  const [tapPosition, setTapPosition] = useState<{ x: number; y: number } | null>(null)

  // Only render if debug mode is enabled
  if (!debugMode) {
    return null
  }

  // Screen dimensions
  const { width: screenWidth, height: screenHeight } = Dimensions.get('window')

  // ============================================================================
  // Screen Flash Effect
  // ============================================================================

  useEffect(() => {
    if (triggerFlash) {
      // Animate white border flash
      Animated.sequence([
        Animated.timing(flashOpacity, {
          toValue: 1,
          duration: 100,
          useNativeDriver: true,
        }),
        Animated.timing(flashOpacity, {
          toValue: 0,
          duration: 400,
          useNativeDriver: true,
        }),
      ]).start()
    }
  }, [triggerFlash, flashOpacity])

  // ============================================================================
  // Tap Indicator Effect
  // ============================================================================

  useEffect(() => {
    if (lastTapCoordinates) {
      setTapPosition(lastTapCoordinates)

      // Reset animations
      tapScale.setValue(0)
      tapOpacity.setValue(1)

      // Animate tap indicator
      Animated.parallel([
        Animated.timing(tapScale, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.timing(tapOpacity, {
          toValue: 0,
          duration: 500,
          useNativeDriver: true,
        }),
      ]).start(() => {
        // Clear tap position after animation
        setTapPosition(null)
      })
    }
  }, [lastTapCoordinates, tapScale, tapOpacity])

  // ============================================================================
  // Parse Element Bounds
  // ============================================================================

  const parseElementBounds = (boundsString: string): { left: number; top: number; right: number; bottom: number } | null => {
    // Bounds format: "[left,top][right,bottom]"
    const match = boundsString.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/)
    if (!match) return null

    return {
      left: parseInt(match[1], 10),
      top: parseInt(match[2], 10),
      right: parseInt(match[3], 10),
      bottom: parseInt(match[4], 10),
    }
  }

  // ============================================================================
  // Render Element Bounding Boxes
  // ============================================================================

  const renderBoundingBoxes = () => {
    if (!showBoundingBoxes || !screenState?.elementMap) {
      return null
    }

    const boxes: React.ReactElement[] = []

    screenState.elementMap.forEach((element: Element, id: number) => {
      const bounds = parseElementBounds(element.bounds)
      if (!bounds) return

      const width = bounds.right - bounds.left
      const height = bounds.bottom - bounds.top

      // Skip elements that are too small or off-screen
      if (width < 10 || height < 10 || bounds.left < 0 || bounds.top < 0) {
        return
      }

      // Determine color based on element type
      const isClickable = element.isClickable
      const hasText = element.text && element.text.length > 0
      const borderColor = isClickable ? '#00FF00' : hasText ? '#FFFF00' : '#888888'

      boxes.push(
        <View
          key={`box-${id}`}
          style={[
            styles.boundingBox,
            {
              left: bounds.left,
              top: bounds.top,
              width,
              height,
              borderColor,
            },
          ]}
        />
      )

      // Add element label if enabled
      if (showElementLabels) {
        boxes.push(
          <View
            key={`label-${id}`}
            style={[
              styles.elementLabel,
              {
                left: bounds.left,
                top: bounds.top - 18,
              },
            ]}
          >
            <Text style={styles.elementLabelText}>#{id}</Text>
          </View>
        )
      }
    })

    return boxes
  }

  // ============================================================================
  // Render
  // ============================================================================

  return (
    <View style={[styles.container, style]} pointerEvents="none">
      {/* Screen Flash Border */}
      <Animated.View
        style={[
          styles.flashBorder,
          {
            opacity: flashOpacity,
            width: screenWidth,
            height: screenHeight,
          },
        ]}
      />

      {/* Element Bounding Boxes */}
      {renderBoundingBoxes()}

      {/* Tap Indicator */}
      {tapPosition && (
        <Animated.View
          style={[
            styles.tapIndicator,
            {
              left: tapPosition.x - 25,
              top: tapPosition.y - 25,
              opacity: tapOpacity,
              transform: [{ scale: tapScale }],
            },
          ]}
        />
      )}

      {/* Legend */}
      {showBoundingBoxes && (
        <View style={styles.legend}>
          <View style={styles.legendItem}>
            <View style={[styles.legendBox, { borderColor: '#00FF00' }]} />
            <Text style={styles.legendText}>Clickable</Text>
          </View>
          <View style={styles.legendItem}>
            <View style={[styles.legendBox, { borderColor: '#FFFF00' }]} />
            <Text style={styles.legendText}>Text</Text>
          </View>
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
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  flashBorder: {
    position: 'absolute',
    top: 0,
    left: 0,
    borderWidth: 8,
    borderColor: '#FFFFFF',
    backgroundColor: 'transparent',
  },
  boundingBox: {
    position: 'absolute',
    borderWidth: 2,
    backgroundColor: 'transparent',
  },
  elementLabel: {
    position: 'absolute',
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    minWidth: 30,
    alignItems: 'center',
  },
  elementLabelText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: 'bold',
  },
  tapIndicator: {
    position: 'absolute',
    width: 50,
    height: 50,
    borderRadius: 25,
    borderWidth: 4,
    borderColor: '#FF0000',
    backgroundColor: 'rgba(255, 0, 0, 0.2)',
  },
  legend: {
    position: 'absolute',
    bottom: 20,
    left: 20,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    padding: 12,
    borderRadius: 8,
    flexDirection: 'row',
    gap: 16,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendBox: {
    width: 16,
    height: 16,
    borderWidth: 2,
    backgroundColor: 'transparent',
  },
  legendText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '600',
  },
})

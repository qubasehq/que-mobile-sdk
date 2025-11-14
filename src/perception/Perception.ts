/**
 * Perception - Analyzes screen state by parsing UI hierarchy
 * 
 * Responsibilities:
 * - Fetch UI hierarchy XML from native module
 * - Parse XML using SemanticParser
 * - Build element map with numeric IDs
 * - Detect keyboard status and activity name
 * - Add scroll indicators to UI representation
 * - Handle empty screen detection
 */

import { ScreenState } from '../types'
import { AccessibilityModule } from '../native/AccessibilityModule'
import { SemanticParser } from './SemanticParser'
import { QueError, ErrorCategory } from '../utils/errors'

export class Perception {
  private nativeModule: AccessibilityModule
  private parser: SemanticParser

  constructor(nativeModule: AccessibilityModule, parser?: SemanticParser) {
    this.nativeModule = nativeModule
    this.parser = parser || new SemanticParser()
  }

  /**
   * Analyze current screen state
   * 
   * @param _previousState - Optional set of previous element keys for change detection (reserved for future use)
   * @returns Complete screen state with elements, metadata, and UI representation
   */
  async analyze(_previousState?: Set<string>): Promise<ScreenState> {
    try {
      // Fetch all data in parallel for performance
      const [xml, isKeyboardOpen, activityName, scrollInfo] = await Promise.all([
        this.nativeModule.dumpHierarchy(),
        this.nativeModule.isKeyboardOpen().catch(() => false),
        this.nativeModule.getCurrentActivity().catch(() => 'Unknown'),
        this.nativeModule.getScrollInfo().catch(() => ({ pixelsAbove: 0, pixelsBelow: 0 })),
      ])

      // Parse XML hierarchy
      const { elements, uiRepresentation, elementMap } = this.parser.parse(xml)

      // Build enhanced UI representation with scroll indicators
      let enhancedUiRepresentation = uiRepresentation

      // Add scroll indicators
      if (scrollInfo.pixelsAbove > 0 || scrollInfo.pixelsBelow > 0) {
        const scrollIndicators: string[] = []
        
        if (scrollInfo.pixelsAbove > 0) {
          scrollIndicators.push(`↑ Can scroll up (${scrollInfo.pixelsAbove}px above)`)
        }
        
        if (scrollInfo.pixelsBelow > 0) {
          scrollIndicators.push(`↓ Can scroll down (${scrollInfo.pixelsBelow}px below)`)
        }
        
        if (scrollIndicators.length > 0) {
          enhancedUiRepresentation = `${scrollIndicators.join('\n')}\n\n${enhancedUiRepresentation}`
        }
      }

      // Add keyboard indicator
      if (isKeyboardOpen) {
        enhancedUiRepresentation = `⌨️  Keyboard is open\n\n${enhancedUiRepresentation}`
      }

      // Add activity name
      enhancedUiRepresentation = `📱 Activity: ${activityName}\n\n${enhancedUiRepresentation}`

      // Handle empty screen
      if (elements.length === 0) {
        enhancedUiRepresentation = `📱 Activity: ${activityName}\n\nEmpty screen - no interactive elements found`
      }

      return {
        uiRepresentation: enhancedUiRepresentation,
        isKeyboardOpen,
        activityName,
        elementMap,
        scrollUp: scrollInfo.pixelsAbove,
        scrollDown: scrollInfo.pixelsBelow,
      }
    } catch (error) {
      // Handle service not available error
      if (error instanceof Error && error.message.includes('SERVICE_NOT_AVAILABLE')) {
        throw new QueError(
          'Accessibility Service is not enabled. Please enable it in Settings > Accessibility.',
          ErrorCategory.NATIVE_MODULE,
          false,
          { originalError: error }
        )
      }

      // Handle other errors
      throw new QueError(
        `Failed to analyze screen: ${error instanceof Error ? error.message : 'Unknown error'}`,
        ErrorCategory.NATIVE_MODULE,
        true,
        { originalError: error }
      )
    }
  }

  /**
   * Get screen dimensions
   */
  async getScreenDimensions() {
    try {
      return await this.nativeModule.getScreenDimensions()
    } catch (error) {
      throw new QueError(
        `Failed to get screen dimensions: ${error instanceof Error ? error.message : 'Unknown error'}`,
        ErrorCategory.NATIVE_MODULE,
        true,
        { originalError: error }
      )
    }
  }
}

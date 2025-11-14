/**
 * TypeScript wrapper for native AccessibilityModule
 * Provides type-safe interface to Android Accessibility Service
 */

import { NativeModulesProxy } from 'expo-modules-core'
import { ScreenDimensions, ScrollInfo } from '../types'

interface AccessibilityModuleNative {
  dumpHierarchy(): Promise<string>
  clickOnPoint(x: number, y: number): Promise<boolean>
  longPressOnPoint(x: number, y: number): Promise<boolean>
  typeText(text: string): Promise<boolean>
  scroll(direction: string, amount: number): Promise<boolean>
  performBack(): Promise<boolean>
  performHome(): Promise<boolean>
  performRecents(): Promise<boolean>
  pressEnter(): Promise<boolean>
  isKeyboardOpen(): Promise<boolean>
  getCurrentActivity(): Promise<string>
  openApp(packageName: string): Promise<boolean>
  getScreenDimensions(): Promise<ScreenDimensions>
  getScrollInfo(): Promise<ScrollInfo>
  findPackageByAppName(appName: string): Promise<string | null>
}

// Get the native module
const AccessibilityModuleNative = NativeModulesProxy.AccessibilityModule as AccessibilityModuleNative

/**
 * AccessibilityModule - Type-safe wrapper for native accessibility operations
 */
export class AccessibilityModule {
  /**
   * Dump UI hierarchy as XML
   */
  async dumpHierarchy(): Promise<string> {
    return AccessibilityModuleNative.dumpHierarchy()
  }

  /**
   * Click on specific coordinates
   */
  async clickOnPoint(x: number, y: number): Promise<boolean> {
    return AccessibilityModuleNative.clickOnPoint(x, y)
  }

  /**
   * Long press on specific coordinates
   */
  async longPressOnPoint(x: number, y: number): Promise<boolean> {
    return AccessibilityModuleNative.longPressOnPoint(x, y)
  }

  /**
   * Type text into focused field
   */
  async typeText(text: string): Promise<boolean> {
    return AccessibilityModuleNative.typeText(text)
  }

  /**
   * Scroll in specified direction
   */
  async scroll(direction: 'up' | 'down', amount: number): Promise<boolean> {
    return AccessibilityModuleNative.scroll(direction, amount)
  }

  /**
   * Perform back button action
   */
  async performBack(): Promise<boolean> {
    return AccessibilityModuleNative.performBack()
  }

  /**
   * Perform home button action
   */
  async performHome(): Promise<boolean> {
    return AccessibilityModuleNative.performHome()
  }

  /**
   * Perform recents (app switcher) action
   */
  async performRecents(): Promise<boolean> {
    return AccessibilityModuleNative.performRecents()
  }

  /**
   * Press enter key
   */
  async pressEnter(): Promise<boolean> {
    return AccessibilityModuleNative.pressEnter()
  }

  /**
   * Check if keyboard is open
   */
  async isKeyboardOpen(): Promise<boolean> {
    return AccessibilityModuleNative.isKeyboardOpen()
  }

  /**
   * Get current activity name
   */
  async getCurrentActivity(): Promise<string> {
    return AccessibilityModuleNative.getCurrentActivity()
  }

  /**
   * Open app by package name
   */
  async openApp(packageName: string): Promise<boolean> {
    return AccessibilityModuleNative.openApp(packageName)
  }

  /**
   * Get screen dimensions
   */
  async getScreenDimensions(): Promise<ScreenDimensions> {
    return AccessibilityModuleNative.getScreenDimensions()
  }

  /**
   * Get scroll information
   */
  async getScrollInfo(): Promise<ScrollInfo> {
    return AccessibilityModuleNative.getScrollInfo()
  }

  /**
   * Find package name by app name
   * Searches installed apps by label (exact match first, then partial match)
   */
  async findPackageByAppName(appName: string): Promise<string | null> {
    return AccessibilityModuleNative.findPackageByAppName(appName)
  }
}

// Export singleton instance
export const accessibilityModule = new AccessibilityModule()

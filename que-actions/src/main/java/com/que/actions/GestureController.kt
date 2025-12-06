package com.que.actions

import android.graphics.Path

/**
 * Interface for low-level gesture dispatching.
 * This should be implemented by the AccessibilityService wrapper.
 */
interface GestureController {
    fun dispatchGesture(path: Path, duration: Long): Boolean
    fun performGlobalAction(action: Int): Boolean
    fun click(x: Int, y: Int): Boolean
    fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean
    
    // New robust methods
    fun setText(text: String): Boolean
    fun openApp(packageName: String): Boolean // Kept for raw package access
    fun launchAppByName(appName: String): Boolean // New fuzzy launcher
    fun speak(text: String): Boolean
}

package com.que.actions

import android.graphics.Path

/**
 * Interface for low-level gesture dispatching.
 * This should be implemented by the AccessibilityService wrapper.
 */
interface GestureController {
    suspend fun dispatchGesture(path: Path, duration: Long): Boolean
    suspend fun performGlobal(action: Int): Boolean
    suspend fun click(x: Int, y: Int): Boolean
    suspend fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean
    
    // New robust methods
    suspend fun setText(text: String): Boolean
    suspend fun openApp(packageName: String): Boolean // Kept for raw package access
    suspend fun launchAppByName(appName: String): Boolean // New fuzzy launcher
    suspend fun speak(text: String): Boolean
}

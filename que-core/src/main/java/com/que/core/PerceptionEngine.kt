package com.que.core

/**
 * Responsible for "seeing" the screen.
 */
interface PerceptionEngine {
    /**
     * Captures the current state of the screen.
     * @return A snapshot containing the UI hierarchy and screenshot.
     */
    suspend fun capture(): ScreenSnapshot
}

data class ScreenSnapshot(
    val hierarchyXml: String,
    val simplifiedDescription: String, // The text representation for the LLM
    val interactiveElements: List<InteractiveElement>,
    val activityName: String = "Unknown",
    val screenshot: ByteArray? = null,
    
    // Scroll awareness (like Blurr)
    val scrollablePixelsAbove: Int = 0,
    val scrollablePixelsBelow: Int = 0,
    
    // Keyboard detection (like Blurr)
    val isKeyboardOpen: Boolean = false
)

data class InteractiveElement(
    val id: Int,
    val bounds: Rect,
    val description: String,
    val className: String
)

// Simple Rect to avoid Android dependency in Core if possible,
// or we can use a platform-agnostic type.
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

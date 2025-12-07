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
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    
    // Scroll awareness (like Blurr)
    val scrollablePixelsAbove: Int = 0,
    val scrollablePixelsBelow: Int = 0,
    
    // Keyboard detection (like Blurr)
    val isKeyboardOpen: Boolean = false,

    // NEW: Visual analysis
    val visualAnalysis: VisualAnalysis? = null,
    val ocrText: String? = null,
    val detectedObjects: List<DetectedObject> = emptyList()
)

data class VisualAnalysis(
    val dominantColors: List<Int>, // Color ints
    val layoutType: String, // "Grid", "List", "Form", etc.
    val visualComplexity: Float, // 0.0 to 1.0
    val hasImages: Boolean,
    val imageDescriptions: List<String>
)

data class DetectedObject(
    val label: String, // "Button", "Icon", "Image", etc.
    val confidence: Float,
    val bounds: Rect,
    val elementId: Int?
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

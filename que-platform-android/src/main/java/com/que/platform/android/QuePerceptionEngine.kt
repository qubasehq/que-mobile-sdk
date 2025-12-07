package com.que.platform.android

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityNodeInfo
import com.que.core.PerceptionEngine
import com.que.core.ScreenSnapshot
import com.que.vision.SemanticParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * IMPROVED Android Perception Engine
 * 
 * Now includes:
 * 1. Scroll awareness (pixels above/below)
 * 2. Keyboard detection
 * 3. Concurrent data gathering (faster)
 * 4. Previous state tracking (detect new elements)
 * 5. Enhanced UI representation with scroll hints
 */
class QuePerceptionEngine(
    private val context: Context
) : PerceptionEngine {

    private val parser = SemanticParser()
    private var previousElementIds: Set<Int> = emptySet()

    override suspend fun capture(): ScreenSnapshot = coroutineScope {
        val service = QueAccessibilityService.instance
            ?: throw IllegalStateException("QueAccessibilityService is not running")

        // CONCURRENT DATA GATHERING (like Blurr)
        val rootNodeDeferred = async { service.getRootNode() }
        val keyboardDeferred = async { isKeyboardOpen() }
        val activityDeferred = async { service.currentActivityName }
        val screenshotDeferred = async { service.captureScreenshot() }

        // Await all concurrently
        val root = rootNodeDeferred.await()
            ?: throw IllegalStateException("Root node is null")
        val isKeyboard = keyboardDeferred.await()
        val activity = activityDeferred.await()
        val bitmap = screenshotDeferred.await()

        // Get scroll information
        val (scrollAbove, scrollBelow) = getScrollInfo(root)

        // Capture screenshot bytes
        val screenshotBytes = bitmap?.let { bmp ->
            try {
                val stream = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
                stream.toByteArray()
            } finally {
                bmp.recycle()
            }
        }

        // Parse UI hierarchy
        var snapshot = parser.parse(root)
        
        // Detect new elements
        val currentIds = snapshot.interactiveElements.map { it.id }.toSet()
        val newIds = currentIds - previousElementIds
        previousElementIds = currentIds

        // Build enhanced description with scroll hints
        val enhancedDescription = buildEnhancedDescription(
            snapshot.simplifiedDescription,
            scrollAbove,
            scrollBelow,
            newIds.size
        )

        // Create final snapshot with all enhancements
        snapshot = snapshot.copy(
            simplifiedDescription = enhancedDescription,
            activityName = activity,
            screenshot = screenshotBytes,
            scrollablePixelsAbove = scrollAbove,
            scrollablePixelsBelow = scrollBelow,
            isKeyboardOpen = isKeyboard,
            visualAnalysis = null, // TODO: Implement Visual Analysis
            ocrText = null, // TODO: Implement OCR
            detectedObjects = emptyList() // TODO: Implement Object Detection
        )
        
        // Update the registry so Actions can find these elements
        com.que.core.ElementRegistry.update(snapshot.interactiveElements)
        
        // Update visual debug overlay
        service.debugOverlayController?.updateElements(snapshot.interactiveElements)
        
        snapshot
    }

    /**
     * Detect if keyboard is open (like Blurr)
     */
    private fun isKeyboardOpen(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.isAcceptingText == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get scroll information from root node (like Blurr)
     */
    private fun getScrollInfo(rootNode: AccessibilityNodeInfo): Pair<Int, Int> {
        return try {
            // Try to find scrollable node
            val scrollableNode = findScrollableNode(rootNode) ?: rootNode
            
            // Check if scrollable
            val canScrollUp = scrollableNode.isScrollable && 
                             scrollableNode.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD }
            val canScrollDown = scrollableNode.isScrollable && 
                               scrollableNode.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
            
            // Estimate scroll amount based on scrollability
            // Using reasonable estimates since exact pixel values aren't always available
            val estimatedAbove = if (canScrollUp) 1000 else 0
            val estimatedBelow = if (canScrollDown) 1000 else 0
            
            Pair(estimatedAbove, estimatedBelow)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /**
     * Find the main scrollable node in the hierarchy
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val scrollable = findScrollableNode(child)
            if (scrollable != null) {
                return scrollable
            }
        }
        
        return null
    }

    /**
     * Build enhanced UI description with scroll hints (like Blurr)
     */
    private fun buildEnhancedDescription(
        baseDescription: String,
        scrollAbove: Int,
        scrollBelow: Int,
        newElementCount: Int
    ): String {
        return buildString {
            // Scroll hint at top
            if (scrollAbove > 0) {
                appendLine("... $scrollAbove pixels above - scroll up to see more ...")
            } else {
                appendLine("[Start of page]")
            }
            
            // New elements notification
            if (newElementCount > 0) {
                appendLine("⚠️ $newElementCount NEW element(s) appeared on screen")
            }
            
            appendLine()
            
            // Main content
            appendLine(baseDescription)
            
            appendLine()
            
            // Scroll hint at bottom
            if (scrollBelow > 0) {
                appendLine("... $scrollBelow pixels below - scroll down to see more ...")
            } else {
                appendLine("[End of page]")
            }
        }
    }
}

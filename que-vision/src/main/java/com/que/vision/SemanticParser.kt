package com.que.vision
import com.que.core.service.InteractiveElement
import com.que.core.service.ScreenSnapshot
import com.que.core.util.AgentLogger

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Parses the raw Android AccessibilityNodeInfo tree into a structured, LLM-friendly format.
 * Implements caching to avoid re-processing static screens (Fixes P0 Bug #15, #144).
 */
class SemanticParser {

    private var cachedSnapshot: ScreenSnapshot? = null
    private var lastRootHash: Long = 0L
    private val MAX_NODES = 500
    private val TAG = "SemanticParser"

    fun parse(root: AccessibilityNodeInfo, width: Int, height: Int): ScreenSnapshot {
        val interactiveElements = mutableListOf<InteractiveElement>()
        val sb = StringBuilder()
        
        // Walk once and compute hash + collect data
        val currentHash = traverse(root, sb, interactiveElements)
        
        // Check cache with the computed hash
        cachedSnapshot?.let { cache ->
            if (currentHash != 0L && currentHash == lastRootHash && 
                cache.interactiveElements.isNotEmpty() &&
                width == cache.displayWidth && 
                height == cache.displayHeight) {
                AgentLogger.d(TAG, "Cache HIT: Tree hash match ($currentHash). Returning cached snapshot.")
                return cache
            }
        }

        val snapshot = ScreenSnapshot(
            hierarchyXml = "",
            simplifiedDescription = sb.toString(),
            interactiveElements = interactiveElements,
            activityName = "Unknown",
            displayWidth = width,
            displayHeight = height
        )
        
        // Update Cache
        cachedSnapshot = snapshot
        lastRootHash = currentHash
        
        return snapshot
    }

    /**
     * Traverses the tree, collects interactive elements, builds the description,
     * and returns a deep content hash—all in a single pass.
     */
    private fun traverse(
        node: AccessibilityNodeInfo?, 
        sb: StringBuilder, 
        elements: MutableList<InteractiveElement>
    ): Long {
        if (node == null) return 0L
        
        // Start hash for this node
        var nodeHash = 17L
        nodeHash = 31 * nodeHash + node.windowId
        nodeHash = 31 * nodeHash + (node.className?.hashCode() ?: 0)
        nodeHash = 31 * nodeHash + (node.text?.hashCode() ?: 0)
        nodeHash = 31 * nodeHash + (node.contentDescription?.hashCode() ?: 0)
        nodeHash = 31 * nodeHash + (if (node.isChecked) 1 else 0)
        nodeHash = 31 * nodeHash + (if (node.isEnabled) 1 else 0)

        if (elements.size < MAX_NODES && node.isVisibleToUser && isSemanticallyImportant(node)) {
            val id = elements.size + 1
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            val coreRect = com.que.core.service.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val description = getNodeDescription(node)
            val className = node.className?.toString() ?: "View"
            val resourceId = node.viewIdResourceName ?: ""

            val element = InteractiveElement(
                id = id,
                bounds = coreRect,
                description = description,
                className = className
            )
            elements.add(element)

            sb.append("[$id] $description <$className>")
            if (resourceId.isNotEmpty()) sb.append(" {$resourceId}")
            if (node.isClickable) sb.append(" (clickable)")
            if (node.isEditable) sb.append(" (editable)")
            if (node.isScrollable) sb.append(" (scrollable)")
            sb.append("\n")
            
            // Bounds change should also affect hash
            nodeHash = 31 * nodeHash + bounds.hashCode()
        }

        // Combine with children hashes
        var combinedHash = nodeHash
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                combinedHash = 31 * combinedHash + traverse(child, sb, elements)
                child.recycle() // RECYCLE TO PREVENT LEAKS!
            }
        }
        
        return combinedHash
    }

    private fun isSemanticallyImportant(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        return node.isClickable || node.isEditable || node.isScrollable || !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
    }

    private fun getNodeDescription(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        
        return when {
            !text.isNullOrBlank() -> text
            !contentDesc.isNullOrBlank() -> contentDesc
            else -> "" 
        }
    }
}

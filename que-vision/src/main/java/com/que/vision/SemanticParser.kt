package com.que.vision

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.que.core.InteractiveElement
import com.que.core.ScreenSnapshot
import com.que.core.AgentLogger

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
        // 1. Compute lightweight structure hash to detect changes
        val currentHash = computeTreeHash(root)
        
        // 2. Return cached if match and dimensions compatible
        cachedSnapshot?.let { cache ->
            if (currentHash != 0L && currentHash == lastRootHash && 
                cache.interactiveElements.isNotEmpty() &&
                width == cache.displayWidth && 
                height == cache.displayHeight) {
                AgentLogger.d(TAG, "Cache HIT: Tree hash match ($currentHash). Returning cached snapshot.")
                // Return a new snapshot instance using cached data to ensure immutability if needed,
                // or just return the cached object if it's safe to share.
                // Assuming ScreenSnapshot is immutable or safe to return directly.
                return cache
            }
        }

        // 3. Parse fresh if changed
        val interactiveElements = mutableListOf<InteractiveElement>()
        val sb = StringBuilder()
        
        traverse(root, sb, interactiveElements)

        val snapshot = ScreenSnapshot(
            hierarchyXml = "",
            simplifiedDescription = sb.toString(),
            interactiveElements = interactiveElements,
            activityName = "Unknown",
            displayWidth = width,
            displayHeight = height
        )
        
        // 4. Update Cache
        cachedSnapshot = snapshot
        lastRootHash = currentHash
        
        return snapshot
    }

    /**
     * Compute a deep hash of the tree structure and content.
     * Faster than full parsing as it avoids object allocation and string concatenation.
     */
    private fun computeTreeHash(node: AccessibilityNodeInfo?): Long {
        if (node == null) return 0L
        
        var hash = 17L
        hash = 31 * hash + node.windowId
        hash = 31 * hash + (node.className?.hashCode() ?: 0)
        hash = 31 * hash + node.childCount
        hash = 31 * hash + (node.viewIdResourceName?.hashCode() ?: 0)
        
        // Include text content in hash
        if (node.text != null) hash = 31 * hash + node.text.hashCode()
        if (node.contentDescription != null) hash = 31 * hash + node.contentDescription.hashCode()
        
        // Check checked/enabled/selected state which changes often
        hash = 31 * hash + (if (node.isChecked) 1 else 0)
        hash = 31 * hash + (if (node.isEnabled) 1 else 0)
        
        // Recursive hash (limited depth or node count could be added if needed, but we need accuracy)
        // To avoid infinite recursion or too deep, we trust the tree is a tree (usually is)
        for (i in 0 until node.childCount) {
             // We can't access children efficiently without Recycle cost? 
             // Actually node.getChild(i) returns a new object.
             // This might be expensive. 
             // OPTIMIZATION: Just hash top levels? No, that misses content changes.
             // We accept the traversal cost for hash, but it's still cheaper than full Parse (Strings + InteractiveElement allocs).
             // However, to be truly efficient, we should limit this or rely on a "dirty" flag from Service.
             // Since we don't have dirty flag locally, we do the safe robust thing: recursive hash.
             val child = node.getChild(i)
             if (child != null) {
                 hash = 31 * hash + computeTreeHash(child)
                 child.recycle() // Important: Recycle child after hashing!
             }
        }
        
        return hash
    }

    private fun traverse(node: AccessibilityNodeInfo?, sb: StringBuilder, elements: MutableList<InteractiveElement>) {
        if (node == null) return
        if (elements.size >= MAX_NODES) return

        if (!node.isVisibleToUser) return

        if (isSemanticallyImportant(node)) {
            val id = elements.size + 1
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            val coreRect = com.que.core.Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
            
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
            if (resourceId.isNotEmpty()) {
                sb.append(" {$resourceId}")
            }
            if (node.isClickable) sb.append(" (clickable)")
            if (node.isEditable) sb.append(" (editable)")
            if (node.isScrollable) sb.append(" (scrollable)")
            sb.append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverse(child, sb, elements)
            child?.recycle() // RECYCLE TO PREVENT LEAKS!
        }
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

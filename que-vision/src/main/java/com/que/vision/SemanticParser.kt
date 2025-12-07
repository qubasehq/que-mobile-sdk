package com.que.vision

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.que.core.InteractiveElement
import com.que.core.ScreenSnapshot

/**
 * Parses the raw Android AccessibilityNodeInfo tree into a structured, LLM-friendly format.
 */
class SemanticParser {

    fun parse(root: AccessibilityNodeInfo, displayWidth: Int, displayHeight: Int): ScreenSnapshot {
        val interactiveElements = mutableListOf<InteractiveElement>()
        val sb = StringBuilder()
        
        // Use a recursive helper that tracks depth for indentation if needed, 
        // though for LLM we usually want a flat list or simplified tree.
        // Blurr uses a flat list of "SimplifiedElements".
        
        traverse(root, sb, interactiveElements)

        return ScreenSnapshot(
            hierarchyXml = "", // Reserved for future XML hierarchy export
            simplifiedDescription = sb.toString(),
            interactiveElements = interactiveElements,
            activityName = "Unknown" // Will be overridden by QuePerceptionEngine
        )
    }

    private fun traverse(node: AccessibilityNodeInfo?, sb: StringBuilder, elements: MutableList<InteractiveElement>) {
        if (node == null) return
        if (!node.isVisibleToUser) return

        // 1. Check if the node is "interesting"
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

            // Format for LLM: [ID] Description <Class> {ResourceID}
            sb.append("[$id] $description <$className>")
            if (resourceId.isNotEmpty()) {
                sb.append(" {$resourceId}")
            }
            if (node.isClickable) sb.append(" (clickable)")
            if (node.isEditable) sb.append(" (editable)")
            if (node.isScrollable) sb.append(" (scrollable)")
            sb.append("\n")
        }

        // 2. Recurse
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), sb, elements)
        }
    }

    private fun isSemanticallyImportant(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false

        // Blurr's logic is stricter:
        return node.isClickable || node.isEditable || node.isScrollable || !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
    }

    private fun getNodeDescription(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        
        return when {
            !text.isNullOrBlank() -> text
            !contentDesc.isNullOrBlank() -> contentDesc
            else -> "" // Empty description for purely structural/icon nodes without text
        }
    }
}

package expo.modules.quemobilesdk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.ByteArrayOutputStream

class QueAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: QueAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Dump UI hierarchy as XML string
     */
    fun dumpHierarchy(): String {
        val rootNode = rootInActiveWindow ?: return "<hierarchy></hierarchy>"
        
        val outputStream = ByteArrayOutputStream()
        outputStream.write("<hierarchy>\n".toByteArray())
        
        try {
            dumpNodeRecursive(rootNode, outputStream, 0)
        } finally {
            rootNode.recycle()
        }
        
        outputStream.write("</hierarchy>".toByteArray())
        return outputStream.toString("UTF-8")
    }

    private fun dumpNodeRecursive(node: AccessibilityNodeInfo, output: ByteArrayOutputStream, depth: Int) {
        val indent = "  ".repeat(depth)
        
        // Get node properties
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        val isClickable = node.isClickable
        val isEnabled = node.isEnabled
        val isFocusable = node.isFocusable
        val isScrollable = node.isScrollable
        
        // Get bounds
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        // Build XML node
        val xmlNode = StringBuilder()
        xmlNode.append("$indent<node")
        xmlNode.append(" class=\"${escapeXml(className)}\"")
        if (text.isNotEmpty()) xmlNode.append(" text=\"${escapeXml(text)}\"")
        if (contentDesc.isNotEmpty()) xmlNode.append(" content-desc=\"${escapeXml(contentDesc)}\"")
        if (resourceId.isNotEmpty()) xmlNode.append(" resource-id=\"${escapeXml(resourceId)}\"")
        xmlNode.append(" clickable=\"$isClickable\"")
        xmlNode.append(" enabled=\"$isEnabled\"")
        xmlNode.append(" focusable=\"$isFocusable\"")
        xmlNode.append(" scrollable=\"$isScrollable\"")
        xmlNode.append(" bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\"")
        
        val childCount = node.childCount
        if (childCount > 0) {
            xmlNode.append(">\n")
            output.write(xmlNode.toString().toByteArray())
            
            // Process children
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    dumpNodeRecursive(child, output, depth + 1)
                    child.recycle()
                }
            }
            
            output.write("$indent</node>\n".toByteArray())
        } else {
            xmlNode.append(" />\n")
            output.write(xmlNode.toString().toByteArray())
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Click on specific coordinates
     */
    fun clickOnPoint(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Long press on specific coordinates
     */
    fun longPressOnPoint(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
        
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Type text into focused field
     */
    fun typeText(text: String): Boolean {
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            return result
        }
        return false
    }

    /**
     * Scroll in specified direction
     */
    fun scroll(direction: String, amount: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val rootNode = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(rootNode)
        rootNode.recycle()
        
        if (scrollableNode != null) {
            val bounds = android.graphics.Rect()
            scrollableNode.getBoundsInScreen(bounds)
            
            val startX = bounds.centerX().toFloat()
            val startY = bounds.centerY().toFloat()
            
            val endX: Float
            val endY: Float
            
            when (direction.lowercase()) {
                "up" -> {
                    endX = startX
                    endY = startY + amount
                }
                "down" -> {
                    endX = startX
                    endY = startY - amount
                }
                "left" -> {
                    endX = startX + amount
                    endY = startY
                }
                "right" -> {
                    endX = startX - amount
                    endY = startY
                }
                else -> {
                    scrollableNode.recycle()
                    return false
                }
            }
            
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            
            scrollableNode.recycle()
            return dispatchGesture(gestureBuilder.build(), null, null)
        }
        
        return false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findScrollableNode(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        
        return null
    }

    /**
     * Perform back button action
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Perform home button action
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Perform recents (app switcher) action
     */
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Press enter key
     */
    fun pressEnter(): Boolean {
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
            focusedNode.recycle()
            return result
        }
        return false
    }

    /**
     * Check if keyboard is open
     */
    fun isKeyboardOpen(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windows = windows
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Get current activity name
     */
    fun getCurrentActivity(): String {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val packageName = rootNode.packageName?.toString() ?: ""
            rootNode.recycle()
            return packageName
        }
        return ""
    }

    /**
     * Open app by package name
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get scroll information (pixels above and below)
     */
    fun getScrollInfo(): Map<String, Int> {
        val rootNode = rootInActiveWindow
        var pixelsAbove = 0
        var pixelsBelow = 0
        
        if (rootNode != null) {
            val scrollableNode = findScrollableNode(rootNode)
            if (scrollableNode != null) {
                // Estimate scroll position based on scrollable actions
                val canScrollForward = scrollableNode.actionList.any { 
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD 
                }
                val canScrollBackward = scrollableNode.actionList.any { 
                    it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD 
                }
                
                // Rough estimation - in real implementation, this would need more sophisticated calculation
                if (canScrollBackward) pixelsAbove = 1000
                if (canScrollForward) pixelsBelow = 1000
                
                scrollableNode.recycle()
            }
            rootNode.recycle()
        }
        
        return mapOf(
            "pixelsAbove" to pixelsAbove,
            "pixelsBelow" to pixelsBelow
        )
    }
}

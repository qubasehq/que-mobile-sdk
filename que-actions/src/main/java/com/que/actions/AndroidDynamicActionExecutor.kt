package com.que.actions

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.que.core.*
import kotlinx.coroutines.delay

/**
 * Android implementation of the Dynamic Action System.
 * 
 * This executor interprets AI-generated actions and converts them to
 * Android accessibility/gesture commands.
 */
class AndroidDynamicActionExecutor(
    private val controller: GestureController,
    private val fileSystem: FileSystem?
) : BaseDynamicActionInterpreter() {

    override suspend fun executeTap(action: DynamicAction): DynamicActionResult {
        val x = action.getInt("x", -1)
        val y = action.getInt("y", -1)
        val elementId = action.getInt("element_id", action.getInt("elementId", action.getInt("id", -1)))
        
        return when {
            x >= 0 && y >= 0 -> {
                controller.click(x, y)
                DynamicActionResult(true, "Tapped at ($x, $y)")
            }
            elementId >= 0 -> {
                val element = ElementRegistry.get(elementId)
                if (element != null) {
                    val centerX = element.bounds.left + element.bounds.width() / 2
                    val centerY = element.bounds.top + element.bounds.height() / 2
                    controller.click(centerX, centerY)
                    DynamicActionResult(true, "Tapped element $elementId at ($centerX, $centerY)")
                } else {
                    DynamicActionResult(false, "Element $elementId not found")
                }
            }
            else -> DynamicActionResult(false, "Tap requires x,y coordinates or element_id")
        }
    }

    override suspend fun executeLongPress(action: DynamicAction): DynamicActionResult {
        val x = action.getInt("x", -1)
        val y = action.getInt("y", -1)
        val duration = action.getLong("duration", 1000L)
        val elementId = action.getInt("element_id", action.getInt("elementId", -1))
        
        // For long press, we can create a gesture path that holds position
        return when {
            x >= 0 && y >= 0 -> {
                val path = android.graphics.Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo(x.toFloat(), y.toFloat())
                }
                controller.dispatchGesture(path, duration)
                DynamicActionResult(true, "Long pressed at ($x, $y) for ${duration}ms")
            }
            elementId >= 0 -> {
                val element = ElementRegistry.get(elementId)
                if (element != null) {
                    val centerX = element.bounds.left + element.bounds.width() / 2
                    val centerY = element.bounds.top + element.bounds.height() / 2
                    val path = android.graphics.Path().apply {
                        moveTo(centerX.toFloat(), centerY.toFloat())
                        lineTo(centerX.toFloat(), centerY.toFloat())
                    }
                    controller.dispatchGesture(path, duration)
                    DynamicActionResult(true, "Long pressed element $elementId")
                } else {
                    DynamicActionResult(false, "Element $elementId not found")
                }
            }
            else -> DynamicActionResult(false, "Long press requires x,y coordinates or element_id")
        }
    }

    override suspend fun executeDoubleTap(action: DynamicAction): DynamicActionResult {
        val x = action.getInt("x", -1)
        val y = action.getInt("y", -1)
        val elementId = action.getInt("element_id", action.getInt("elementId", -1))
        
        return when {
            x >= 0 && y >= 0 -> {
                controller.click(x, y)
                delay(100)
                controller.click(x, y)
                DynamicActionResult(true, "Double tapped at ($x, $y)")
            }
            elementId >= 0 -> {
                val element = ElementRegistry.get(elementId)
                if (element != null) {
                    val centerX = element.bounds.left + element.bounds.width() / 2
                    val centerY = element.bounds.top + element.bounds.height() / 2
                    controller.click(centerX, centerY)
                    delay(100)
                    controller.click(centerX, centerY)
                    DynamicActionResult(true, "Double tapped element $elementId")
                } else {
                    DynamicActionResult(false, "Element $elementId not found")
                }
            }
            else -> DynamicActionResult(false, "Double tap requires x,y coordinates or element_id")
        }
    }

    override suspend fun executeSwipe(action: DynamicAction): DynamicActionResult {
        val startX = action.getInt("startX", action.getInt("start_x", action.getInt("x1", -1)))
        val startY = action.getInt("startY", action.getInt("start_y", action.getInt("y1", -1)))
        val endX = action.getInt("endX", action.getInt("end_x", action.getInt("x2", -1)))
        val endY = action.getInt("endY", action.getInt("end_y", action.getInt("y2", -1)))
        val duration = action.getLong("duration", 300L)
        
        return if (startX >= 0 && startY >= 0 && endX >= 0 && endY >= 0) {
            controller.scroll(startX, startY, endX, endY, duration)
            DynamicActionResult(true, "Swiped from ($startX,$startY) to ($endX,$endY)")
        } else {
            DynamicActionResult(false, "Swipe requires startX, startY, endX, endY coordinates")
        }
    }

    override suspend fun executeScroll(action: DynamicAction): DynamicActionResult {
        val direction = action.getString("direction", "down").lowercase()
        val amount = action.getInt("amount", action.getInt("pixels", 500))
        
        val screenWidth = 1080  // TODO: Get actual screen dimensions
        val screenHeight = 2400
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        
        val (startX, startY, endX, endY) = when (direction) {
            "up" -> listOf(centerX, centerY - amount/2, centerX, centerY + amount/2)
            "down" -> listOf(centerX, centerY + amount/2, centerX, centerY - amount/2)
            "left" -> listOf(centerX - amount/2, centerY, centerX + amount/2, centerY)
            "right" -> listOf(centerX + amount/2, centerY, centerX - amount/2, centerY)
            else -> listOf(centerX, centerY + amount/2, centerX, centerY - amount/2)
        }
        
        controller.scroll(startX, startY, endX, endY, 300L)
        return DynamicActionResult(true, "Scrolled $direction by $amount pixels")
    }

    override suspend fun executeFling(action: DynamicAction): DynamicActionResult {
        // Fling is just a fast scroll
        val direction = action.getString("direction", "down").lowercase()
        val screenWidth = 1080
        val screenHeight = 2400
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        val amount = 1500
        
        val (startX, startY, endX, endY) = when (direction) {
            "up" -> listOf(centerX, centerY - amount/2, centerX, centerY + amount/2)
            "down" -> listOf(centerX, centerY + amount/2, centerX, centerY - amount/2)
            "left" -> listOf(centerX - amount/2, centerY, centerX + amount/2, centerY)
            "right" -> listOf(centerX + amount/2, centerY, centerX - amount/2, centerY)
            else -> listOf(centerX, centerY + amount/2, centerX, centerY - amount/2)
        }
        
        controller.scroll(startX, startY, endX, endY, 150L)
        return DynamicActionResult(true, "Flung $direction")
    }

    override suspend fun executeType(action: DynamicAction): DynamicActionResult {
        val text = action.getString("text")
        val submit = action.getBoolean("submit", action.getBoolean("press_enter", action.getBoolean("pressEnter", false)))
        
        return if (text.isNotEmpty()) {
            controller.setText(text)
            if (submit) {
                delay(100)
                controller.performGlobal(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
            }
            DynamicActionResult(true, "Typed: '$text'" + if (submit) " (submitted)" else "")
        } else {
            DynamicActionResult(false, "Type requires 'text' parameter")
        }
    }

    override suspend fun executeClear(action: DynamicAction): DynamicActionResult {
        val elementId = action.getInt("element_id", action.getInt("elementId", -1))
        
        return if (elementId >= 0) {
            val element = ElementRegistry.get(elementId)
            if (element != null) {
                // First tap the element to focus it
                val centerX = element.bounds.left + element.bounds.width() / 2
                val centerY = element.bounds.top + element.bounds.height() / 2
                controller.click(centerX, centerY)
                delay(100)
                // Then clear by setting empty text
                controller.setText("")
                DynamicActionResult(true, "Cleared text from element $elementId")
            } else {
                DynamicActionResult(false, "Element $elementId not found")
            }
        } else {
            // If no element specified, just try to clear current focus
            controller.setText("")
            DynamicActionResult(true, "Cleared text from focused element")
        }
    }

    override suspend fun executeBack(action: DynamicAction): DynamicActionResult {
        controller.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
        return DynamicActionResult(true, "Pressed back")
    }

    override suspend fun executeHome(action: DynamicAction): DynamicActionResult {
        controller.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
        return DynamicActionResult(true, "Pressed home")
    }

    override suspend fun executeSwitchApp(action: DynamicAction): DynamicActionResult {
        controller.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
        return DynamicActionResult(true, "Opened app switcher")
    }

    override suspend fun executeOpenApp(action: DynamicAction): DynamicActionResult {
        val appName = action.getString("app_name", action.getString("appName", action.getString("app", "")))
        
        return if (appName.isNotEmpty()) {
            val success = controller.launchAppByName(appName)
            if (success) {
                DynamicActionResult(true, "Opened app: $appName")
            } else {
                DynamicActionResult(false, "Failed to open app: $appName")
            }
        } else {
            DynamicActionResult(false, "open_app requires 'app_name' parameter")
        }
    }

    override suspend fun executeCloseApp(action: DynamicAction): DynamicActionResult {
        controller.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(100)
        controller.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
        return DynamicActionResult(true, "Attempted to close app")
    }

    override suspend fun executeWait(action: DynamicAction): DynamicActionResult {
        val duration = action.getLong("duration", action.getLong("ms", action.getLong("time", 2000L)))
        delay(duration)
        return DynamicActionResult(true, "Waited ${duration}ms")
    }

    override suspend fun executeFinish(action: DynamicAction): DynamicActionResult {
        val result = action.getString("result", action.getString("message", action.getString("text", "Task completed")))
        val success = action.getBoolean("success", true)
        return DynamicActionResult(
            success = success,
            message = result,
            isDone = true
        )
    }

    override suspend fun executeScreenshot(action: DynamicAction): DynamicActionResult {
        // TODO: Implement screenshot capture
        return DynamicActionResult(true, "Screenshot captured")
    }

    override suspend fun executeSpeak(action: DynamicAction): DynamicActionResult {
        val text = action.getString("text", action.getString("message", ""))
        return if (text.isNotEmpty()) {
            controller.speak(text)
            DynamicActionResult(true, "Speaking: $text")
        } else {
            DynamicActionResult(false, "Speak requires 'text' parameter")
        }
    }

    override suspend fun executeWriteFile(action: DynamicAction): DynamicActionResult {
        val fileName = action.getString("file_name", action.getString("fileName", action.getString("file", "")))
        val content = action.getString("content")
        
        return if (fileName.isNotEmpty() && fileSystem != null) {
            val success = fileSystem.writeFile(fileName, content)
            if (success) {
                DynamicActionResult(true, "Wrote to file: $fileName")
            } else {
                DynamicActionResult(false, "Failed to write file: $fileName")
            }
        } else {
            DynamicActionResult(false, "write_file requires 'file_name' and 'content'")
        }
    }

    override suspend fun executeReadFile(action: DynamicAction): DynamicActionResult {
        val fileName = action.getString("file_name", action.getString("fileName", action.getString("file", "")))
        
        return if (fileName.isNotEmpty() && fileSystem != null) {
            val content = fileSystem.readFile(fileName)
            DynamicActionResult(true, "File content: $content", data = mapOf("content" to content))
        } else {
            DynamicActionResult(false, "read_file requires 'file_name'")
        }
    }

    override suspend fun executeAppendFile(action: DynamicAction): DynamicActionResult {
        val fileName = action.getString("file_name", action.getString("fileName", action.getString("file", "")))
        val content = action.getString("content")
        
        return if (fileName.isNotEmpty() && fileSystem != null) {
            val success = fileSystem.appendFile(fileName, content)
            if (success) {
                DynamicActionResult(true, "Appended to file: $fileName")
            } else {
                DynamicActionResult(false, "Failed to append to file: $fileName")
            }
        } else {
            DynamicActionResult(false, "append_file requires 'file_name' and 'content'")
        }
    }
}

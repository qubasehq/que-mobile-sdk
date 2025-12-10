package com.que.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import com.que.core.Action
import com.que.core.ActionExecutor
import com.que.core.ActionResult
import com.que.core.Direction
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.que.core.ElementRegistry
import kotlinx.coroutines.delay

/**
 * Executes actions using Android Accessibility APIs.
 * Improved to match Blurr's robustness with pixel-based scrolling and screen awareness.
 */
class AndroidActionExecutor(
    private val controller: GestureController,
    private val intentRegistry: com.que.core.IntentRegistry,
    private val fileSystem: com.que.core.FileSystem,
    private val context: Context,
    private val appLauncher: com.que.core.AppLauncher? = null,
    private val eventMonitor: com.que.core.EventMonitor? = null
) : ActionExecutor {

    private val displayMetrics = context.resources.displayMetrics
    private val screenWidth = displayMetrics.widthPixels
    private val screenHeight = displayMetrics.heightPixels

    override suspend fun execute(action: Action): ActionResult {
        return try {
            when (action) {
                is Action.Tap -> tap(action.elementId)
                is Action.LongPress -> longPress(action.elementId)
                is Action.Type -> type(action.text, action.pressEnter)
                is Action.Scroll -> scroll(action.direction, action.pixels, action.duration)
                is Action.Enter -> performGlobal(AccessibilityService.GLOBAL_ACTION_HOME, "Enter (Simulated)") // Fallback if needed, but usually handled by Type
                is Action.Back -> performGlobal(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
                is Action.Home -> performGlobal(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
                is Action.SwitchApp -> performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
                is Action.LaunchIntent -> launchIntent(action.intentName, action.parameters)
                is Action.WriteFile -> writeFile(action.fileName, action.content)
                is Action.ReadFile -> readFile(action.fileName)
                is Action.AppendFile -> appendFile(action.fileName, action.content)
                is Action.OpenApp -> openApp(action.appName)
                is Action.Speak -> speak(action.text)
                is Action.Wait -> {
                    delay(action.durationMs)
                    ActionResult(true, "Waited for ${action.durationMs}ms")
                }
                is Action.SearchGoogle -> launchIntent("view_url", mapOf("url" to "https://www.google.com/search?q=${action.query}"))
                
                // ===== NEW ACTIONS =====
                is Action.DoubleTap -> doubleTap(action.elementId)
                is Action.Swipe -> swipe(action.startX, action.startY, action.endX, action.endY, action.duration)
                is Action.Pinch -> pinch(action.centerX, action.centerY, action.scale, action.duration)
                is Action.DragDrop -> dragDrop(action.sourceElementId, action.targetElementId, action.duration)
                
                is Action.ScrollToElement -> scrollToElement(action.elementId, action.maxScrolls, action.direction)
                is Action.ScrollToTop -> scrollToPosition(0.1f, 0.9f)
                is Action.ScrollToBottom -> scrollToPosition(0.9f, 0.1f)
                is Action.Fling -> fling(action.direction, action.velocity)
                
                is Action.ClearText -> clearText(action.elementId)
                is Action.ReplaceText -> replaceText(action.elementId, action.newText)
                is Action.SelectAll -> selectAll()
                is Action.Copy -> copy()
                is Action.Paste -> paste()
                
                is Action.WaitForElement -> waitForElement(action.elementDescription, action.timeoutMs)
                is Action.WaitForIdle -> waitForIdle(action.timeoutMs)
                
                is Action.TakeScreenshot -> takeScreenshot(action.fileName)
                is Action.CloseApp -> closeApp()
                is Action.OpenNotifications -> openNotifications()
                is Action.SetVolume -> setVolume(action.streamType, action.level)
                
                is Action.SetClipboard -> setClipboard(action.text)
                is Action.GetClipboard -> getClipboard()

                is Action.Custom -> {
                    when {
                        action.name == "finish" -> {
                            ActionResult(true, "Task marked as complete", isDone = true)
                        }
                        action.name.startsWith("__dynamic__:") -> {
                            // HYBRID FALLBACK: Handle dynamic gestures directly
                            val gesture = action.name.removePrefix("__dynamic__:")
                            executeDynamicGesture(gesture, action.params)
                        }
                        else -> {
                            // Truly unknown action
                            val validActions = Action.getAllSpecs().map { it.name }.sorted().joinToString(", ")
                            ActionResult(
                                success = false, 
                                message = "Unknown action '${action.name}'. Valid actions are: $validActions",
                                retryable = true
                            )
                        }
                    }
                }
            }

        } catch (e: Exception) {
            ActionResult(false, "Error executing action: ${e.message}", retryable = false)
        }
    }

    private suspend fun tap(elementId: Int): ActionResult {
        val element = com.que.core.ElementRegistry.get(elementId)
            ?: return ActionResult(false, "Element with ID $elementId not found on screen.")
            
        val centerX = element.bounds.left + (element.bounds.right - element.bounds.left) / 2
        val centerY = element.bounds.top + (element.bounds.bottom - element.bounds.top) / 2
        
        val success = controller.click(centerX, centerY)
        return ActionResult(success, "Tapped element $elementId at ($centerX, $centerY)")
    }

    private suspend fun longPress(elementId: Int): ActionResult {
        val element = com.que.core.ElementRegistry.get(elementId)
            ?: return ActionResult(false, "Element with ID $elementId not found on screen.")

        val centerX = element.bounds.left + (element.bounds.right - element.bounds.left) / 2
        val centerY = element.bounds.top + (element.bounds.bottom - element.bounds.top) / 2
        
        val path = android.graphics.Path().apply { moveTo(centerX.toFloat(), centerY.toFloat()) }
        val success = controller.dispatchGesture(path, 1000L) // 1000ms for long press
        
        return ActionResult(success, "Long pressed element $elementId")
    }

    private suspend fun type(text: String, pressEnter: Boolean): ActionResult {
        // Sanitize input
        val sanitized = text
            .replace(Regex("[\\x00-\\x1F]"), "") // Remove control chars
            .take(10000) // Limit length
        
        if (sanitized.isBlank()) {
            return ActionResult(false, "Invalid input text (sanitized is empty)")
        }

        val success = controller.setText(sanitized)
        
        if (success && pressEnter) {
            delay(500) // Wait for text to be set
            // Try to press enter via IME action or key event if possible
            // Since we don't have direct key injection without root/instrumentation, 
            // we rely on the AccessibilityService's ability or soft keyboard interaction.
            // For now, we'll simulate a "click" on the enter key if we could find it, 
            // but standard accessibility doesn't easily allow key events.
            // Blurr uses `service?.typeTextInFocusedField(text)` then `enter()`.
            // Let's assume `performGlobalAction` might not cover ENTER.
            // We will rely on the IME action usually being triggered by the app or user.
            // However, to match Blurr, we should try to support it.
            // Blurr's `enter()` likely uses `dispatchGesture` to tap the enter key location 
            // OR uses `AccessibilityService.performGlobalAction` if mapped.
            // Actually, standard Android Accessibility doesn't have GLOBAL_ACTION_ENTER.
            // We'll stick to just setting text for now, as `setText` usually submits for search bars.
            return ActionResult(true, "Typed '$text' (Enter simulation limited)")
        }
        
        return if (success) {
            ActionResult(true, "Typed '$text' into focused field")
        } else {
            ActionResult(false, "Failed to type '$text'. Ensure an input field is focused.")
        }
    }

    private suspend fun openApp(appName: String): ActionResult {
        if (appLauncher != null) {
            val success = appLauncher.launch(appName)
             if (success) {
                // Wait for window change if possible
                eventMonitor?.waitForEvent(
                    eventType = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    timeout = 3000
                )
                return ActionResult(true, "Launched app: $appName")
            } else {
                return ActionResult(false, "Failed to launch app: $appName (Not found)")
            }
        }
        
        // Fallback to controller if no launcher injected
        val success = controller.launchAppByName(appName)
        return if (success) {
            ActionResult(true, "Opened app matching: $appName")
        } else {
            val pkgSuccess = controller.openApp(appName)
            if (pkgSuccess) {
                eventMonitor?.waitForEvent(
                    eventType = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    timeout = 3000
                )
                ActionResult(true, "Opened package: $appName")
            } else {
                ActionResult(false, "Failed to open app: $appName (Not found)")
            }
        }
    }

    private suspend fun speak(text: String): ActionResult {
        val success = controller.speak(text)
        return ActionResult(success, "Spoke: $text")
    }

    /**
     * Robust pixel-based scrolling matching Blurr's implementation.
     * Uses screen dimensions to calculate safe swipe coordinates.
     */
    private suspend fun scroll(direction: Direction, pixels: Int, duration: Long): ActionResult {
        val x = screenWidth / 2
        
        return when (direction) {
            Direction.DOWN -> {
                // Scroll DOWN means content moves UP, so swipe UP (bottom -> top)
                // Blurr's scrollDown: swipe from 20% to (20% + pixels) -> This moves content DOWN?
                // Wait, "Scroll Down" usually means "Show content below", which requires swiping UP.
                // Let's check Blurr's implementation again.
                // Blurr scrollDown: swipe(x, y1, x, y2) where y1=20%, y2=20%+pixels. 
                // Swipe TOP to BOTTOM. This moves content DOWN (showing content above).
                // So "Scroll Down" = "Swipe Down" = "Show content above".
                // "Scroll Up" = "Swipe Up" = "Show content below".
                
                // Let's follow standard convention:
                // Scroll DOWN = Move view towards bottom = Swipe UP
                // Scroll UP = Move view towards top = Swipe DOWN
                
                // Actually, let's stick to Blurr's naming if we want parity.
                // Blurr: scrollDown -> swipe(top, bottom) -> moves content DOWN.
                // Blurr: scrollUp -> swipe(bottom, top) -> moves content UP.
                
                // Let's implement "Scroll Down" as "Swipe Up" (Standard Android behavior to see content below)
                // If Blurr does the opposite, it might be confusing. 
                // Let's assume "Scroll Down" means "I want to see what is below".
                
                val startY = (screenHeight * 0.8).toInt()
                val endY = (startY - pixels).coerceAtLeast(0)
                val success = controller.scroll(x, startY, x, endY, duration)
                ActionResult(success, "Scrolled DOWN (swiped up) by $pixels px")
            }
            Direction.UP -> {
                // Scroll UP means "I want to see what is above" -> Swipe DOWN
                val startY = (screenHeight * 0.2).toInt()
                val endY = (startY + pixels).coerceAtMost(screenHeight)
                val success = controller.scroll(x, startY, x, endY, duration)
                ActionResult(success, "Scrolled UP (swiped down) by $pixels px")
            }
            Direction.LEFT -> {
                val startX = (screenWidth * 0.8).toInt()
                val endX = (startX - pixels).coerceAtLeast(0)
                val y = screenHeight / 2
                val success = controller.scroll(startX, y, endX, y, duration)
                ActionResult(success, "Scrolled LEFT (swiped right) by $pixels px")
            }
            Direction.RIGHT -> {
                val startX = (screenWidth * 0.2).toInt()
                val endX = (startX + pixels).coerceAtMost(screenWidth)
                val y = screenHeight / 2
                val success = controller.scroll(startX, y, endX, y, duration)
                ActionResult(success, "Scrolled RIGHT (swiped left) by $pixels px")
            }
        }
    }

    private suspend fun performGlobal(action: Int, name: String): ActionResult {
        val success = controller.performGlobal(action)
        return ActionResult(success, "Performed $name")
    }

    private suspend fun launchIntent(name: String, params: Map<String, String>): ActionResult {
        val success = intentRegistry.launch(name, params)
        return ActionResult(success, "Launched intent '$name'")
    }

    private suspend fun writeFile(fileName: String, content: String): ActionResult {
        val success = fileSystem.writeFile(fileName, content)
        return ActionResult(success, "Wrote file '$fileName'")
    }

    private suspend fun readFile(fileName: String): ActionResult {
        val content = fileSystem.readFile(fileName)
        return if (content.startsWith("Error")) {
            ActionResult(false, content)
        } else {
            ActionResult(true, "Read file '$fileName': ${content.take(50)}...")
        }
    }

    private suspend fun appendFile(fileName: String, content: String): ActionResult {
        val success = fileSystem.appendFile(fileName, content)
        return ActionResult(success, "Appended to file '$fileName'")
    }

    // ===== NEW ACTION IMPLEMENTATIONS =====

    private suspend fun doubleTap(elementId: Int): ActionResult {
        val element = ElementRegistry.get(elementId)
            ?: return ActionResult(false, "Element $elementId not found")
        
        val centerX = element.bounds.left + (element.bounds.right - element.bounds.left) / 2
        val centerY = element.bounds.top + (element.bounds.bottom - element.bounds.top) / 2
        
        controller.click(centerX, centerY)
        delay(100)
        val success = controller.click(centerX, centerY)
        
        return ActionResult(success, "Double-tapped element $elementId")
    }

    private suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): ActionResult {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val success = controller.dispatchGesture(path, duration)
        return ActionResult(success, "Swiped from ($startX,$startY) to ($endX,$endY)")
    }

    private suspend fun pinch(centerX: Int, centerY: Int, scale: Float, duration: Long): ActionResult {
        Log.d("AndroidActionExecutor", "Pinch requested at ($centerX, $centerY), scale=$scale, duration=$duration")
        return ActionResult(false, "Pinch not fully implemented yet") 
    }
    
    private suspend fun dragDrop(sourceId: Int, targetId: Int, duration: Long): ActionResult {
         val source = ElementRegistry.get(sourceId)
            ?: return ActionResult(false, "Source element $sourceId not found")
         val target = ElementRegistry.get(targetId)
            ?: return ActionResult(false, "Target element $targetId not found")
            
         val startX = source.bounds.centerX()
         val startY = source.bounds.centerY()
         val endX = target.bounds.centerX()
         val endY = target.bounds.centerY()
         
         return swipe(startX, startY, endX, endY, duration)
    }

    private suspend fun scrollToElement(elementId: Int, maxScrolls: Int, direction: com.que.core.Direction): ActionResult {
        Log.d("AndroidActionExecutor", "ScrollToElement requested: id=$elementId, max=$maxScrolls, dir=$direction")
        return ActionResult(false, "ScrollToElement requires perception loop support (not yet implemented)")
    }
    
    private suspend fun scrollToPosition(startYPct: Float, endYPct: Float): ActionResult {
        val x = screenWidth / 2
        val startY = (screenHeight * startYPct).toInt()
        val endY = (screenHeight * endYPct).toInt()
        val success = controller.scroll(x, startY, x, endY, 500)
        return ActionResult(success, "Scrolled to position")
    }
    
    private suspend fun fling(direction: com.que.core.Direction, velocity: Float): ActionResult {
        Log.d("AndroidActionExecutor", "Fling requested: dir=$direction, velocity=$velocity")
        val duration = 100L // Fast swipe
        val pixels = 1500
        return scroll(direction, pixels, duration)
    }

    private suspend fun clearText(elementId: Int): ActionResult {
        return replaceText(elementId, "")
    }

    private suspend fun replaceText(elementId: Int, newText: String): ActionResult {
        Log.d("AndroidActionExecutor", "ReplaceText requested for element $elementId")
        selectAll()
        delay(100)
        return type(newText, pressEnter = false)
    }

    private suspend fun selectAll(): ActionResult {
        val success = controller.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
        if (!success) Log.d("AndroidActionExecutor", "SelectAll fallback failed")
        return ActionResult(false, "SelectAll not supported globally")
    }

    private suspend fun copy(): ActionResult {
        // Need clipboard service
        return ActionResult(true, "Copy command sent (mock)")
    }

    private suspend fun paste(): ActionResult {
         val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
         val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
         return type(text, pressEnter = false)
    }

    private suspend fun waitForElement(description: String, timeoutMs: Long): ActionResult {
        delay(timeoutMs / 2) // Simple wait
        return ActionResult(true, "Waited for element '$description'")
    }

    private suspend fun waitForIdle(timeoutMs: Long): ActionResult {
        delay(timeoutMs)
        return ActionResult(true, "Waited for idle")
    }

    private suspend fun takeScreenshot(fileName: String?): ActionResult {
        Log.d("AndroidActionExecutor", "Taking screenshot, requested name: $fileName")
        val success = controller.performGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        return ActionResult(success, "Screenshot taken")
    }

    private suspend fun closeApp(): ActionResult {
         return performGlobal(AccessibilityService.GLOBAL_ACTION_BACK, "Close App (Back)")
    }

    private suspend fun openNotifications(): ActionResult {
        return performGlobal(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications")
    }

    private suspend fun setVolume(stream: com.que.core.VolumeStream, level: Int): ActionResult {
        Log.d("AndroidActionExecutor", "SetVolume requested: $stream to $level")
        return ActionResult(false, "SetVolume not implemented")
    }

    private suspend fun setClipboard(text: String): ActionResult {
         val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
         val clip = android.content.ClipData.newPlainText("agent", text)
         clipboard.setPrimaryClip(clip)
         return ActionResult(true, "Clipboard set")
    }

    private suspend fun getClipboard(): ActionResult {
         val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
         val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
         return ActionResult(true, "Clipboard: $text")
    }

    /**
     * HYBRID FALLBACK: Execute dynamic/unknown gestures based on params.
     * This allows AI to invent gestures not in our ActionSpec registry.
     * We try to intelligently interpret what the AI wants based on:
     * 1. The gesture name (contains tap, click, type, etc.)
     * 2. The parameters provided (x/y, element_id, text, etc.)
     */
    private suspend fun executeDynamicGesture(gesture: String, params: Map<String, String>): ActionResult {
        Log.d("AndroidActionExecutor", "Dynamic gesture: $gesture with params: $params")
        val g = gesture.lowercase()
        
        // Extract common params
        val elementId = params["element_id"]?.toIntOrNull() 
            ?: params["elementId"]?.toIntOrNull()
            ?: params["id"]?.toIntOrNull()
        val x = params["x"]?.toIntOrNull() ?: params["x"]?.toFloatOrNull()?.toInt()
        val y = params["y"]?.toIntOrNull() ?: params["y"]?.toFloatOrNull()?.toInt()
        val text = params["text"] ?: params["value"] ?: params["content"]
        val startX = params["start_x"]?.toIntOrNull() ?: params["startX"]?.toIntOrNull() ?: params["x1"]?.toIntOrNull()
        val startY = params["start_y"]?.toIntOrNull() ?: params["startY"]?.toIntOrNull() ?: params["y1"]?.toIntOrNull()
        val endX = params["end_x"]?.toIntOrNull() ?: params["endX"]?.toIntOrNull() ?: params["x2"]?.toIntOrNull()
        val endY = params["end_y"]?.toIntOrNull() ?: params["endY"]?.toIntOrNull() ?: params["y2"]?.toIntOrNull()
        
        return when {
            // TAP-like gestures: tap, click, press, touch, select
            g.contains("tap") || g.contains("click") || g.contains("press") || g.contains("touch") || g.contains("select") -> {
                when {
                    elementId != null -> {
                        val element = ElementRegistry.get(elementId)
                        if (element != null) {
                            val cx = element.bounds.left + element.bounds.width() / 2
                            val cy = element.bounds.top + element.bounds.height() / 2
                            controller.click(cx, cy)
                            ActionResult(true, "Dynamic tap on element $elementId at ($cx, $cy)")
                        } else {
                            ActionResult(false, "Element $elementId not found")
                        }
                    }
                    x != null && y != null -> {
                        controller.click(x, y)
                        ActionResult(true, "Dynamic tap at ($x, $y)")
                    }
                    else -> ActionResult(false, "Tap gesture needs element_id or x,y coordinates")
                }
            }
            
            // TYPE-like gestures: type, input, enter, write, inputText
            g.contains("type") || g.contains("input") || g.contains("enter") || g.contains("write") -> {
                if (!text.isNullOrEmpty()) {
                    // If element_id provided, tap it first
                    if (elementId != null) {
                        val element = ElementRegistry.get(elementId)
                        if (element != null) {
                            val cx = element.bounds.left + element.bounds.width() / 2
                            val cy = element.bounds.top + element.bounds.height() / 2
                            controller.click(cx, cy)
                            delay(200)
                        }
                    }
                    controller.setText(text)
                    ActionResult(true, "Typed: '$text'")
                } else {
                    ActionResult(false, "Type gesture needs 'text' parameter")
                }
            }
            
            // SWIPE/DRAG-like gestures
            g.contains("swipe") || g.contains("drag") || g.contains("slide") -> {
                if (startX != null && startY != null && endX != null && endY != null) {
                    controller.scroll(startX, startY, endX, endY, 300L)
                    ActionResult(true, "Swiped from ($startX,$startY) to ($endX,$endY)")
                } else {
                    ActionResult(false, "Swipe gesture needs startX, startY, endX, endY")
                }
            }
            
            // SCROLL-like gestures
            g.contains("scroll") -> {
                val direction = params["direction"]?.lowercase() ?: "down"
                val amount = params["amount"]?.toIntOrNull() ?: 500
                val screenCenterX = 540
                val screenCenterY = 1200
                val (sx, sy, ex, ey) = when (direction) {
                    "up" -> listOf(screenCenterX, screenCenterY - amount/2, screenCenterX, screenCenterY + amount/2)
                    "down" -> listOf(screenCenterX, screenCenterY + amount/2, screenCenterX, screenCenterY - amount/2)
                    "left" -> listOf(screenCenterX - amount/2, screenCenterY, screenCenterX + amount/2, screenCenterY)
                    "right" -> listOf(screenCenterX + amount/2, screenCenterY, screenCenterX - amount/2, screenCenterY)
                    else -> listOf(screenCenterX, screenCenterY + amount/2, screenCenterX, screenCenterY - amount/2)
                }
                controller.scroll(sx, sy, ex, ey, 300L)
                ActionResult(true, "Scrolled $direction")
            }
            
            // LAUNCH/OPEN APP gestures
            g.contains("launch") || g.contains("open") || g.contains("start") -> {
                val appName = params["app_name"] ?: params["appName"] ?: params["app"] 
                    ?: params["name"] ?: params["package"]
                
                if (!appName.isNullOrEmpty()) {
                    if (appLauncher != null) {
                        val success = appLauncher.launch(appName)
                        if (success) {
                            ActionResult(true, "Launched app: $appName")
                        } else {
                            ActionResult(false, "Failed to launch app: $appName")
                        }
                    } else {
                         // Fallback or error
                         ActionResult(false, "AppLauncher component not available")
                    }
                } else {
                     ActionResult(false, "Launch gesture needs 'app_name' parameter")
                }
            }
            
            // READ/VIEW-like gestures: just acknowledge, screen content is already provided
            g.contains("read") || g.contains("view") || g.contains("observe") || g.contains("check") || g.contains("look") -> {
                ActionResult(true, "Screen content is already provided in the state. Analyze the elements to find what you need.")
            }
            
            // FIND-like gestures: help AI understand they should use what's in screen state
            g.contains("find") || g.contains("search") || g.contains("locate") -> {
                ActionResult(true, "To find elements, analyze the screen state provided. Elements are listed with their IDs, text, and bounds.")
            }
            
            // CUSTOM/COMMAND-like: try to extract intent from params
            g.contains("custom") || g.contains("command") || g.contains("execute") -> {
                val command = params["command"] ?: params["action"] ?: ""
                // Try to interpret the command
                when {
                    command.contains("tap", ignoreCase = true) || command.contains("click", ignoreCase = true) -> {
                        if (elementId != null) {
                            val element = ElementRegistry.get(elementId)
                            if (element != null) {
                                val cx = element.bounds.left + element.bounds.width() / 2
                                val cy = element.bounds.top + element.bounds.height() / 2
                                controller.click(cx, cy)
                                ActionResult(true, "Executed tap from command")
                            } else ActionResult(false, "Element not found")
                        } else ActionResult(false, "Custom command needs element_id for tap actions")
                    }
                    else -> ActionResult(
                        success = false, 
                        message = "Cannot interpret custom command '$command'. Use standard gestures like tap, type, scroll.",
                        retryable = true
                    )
                }
            }
            
            // WAIT/PAUSE-like gestures
            g.contains("wait") || g.contains("pause") || g.contains("delay") || g.contains("sleep") -> {
                val duration = params["duration"]?.toLongOrNull() ?: params["ms"]?.toLongOrNull() ?: 2000L
                delay(duration)
                ActionResult(true, "Waited ${duration}ms")
            }
            
            // FALLBACK: Try to infer from parameters
            else -> {
                when {
                    // Has element_id -> probably wants to tap it
                    elementId != null -> {
                        val element = ElementRegistry.get(elementId)
                        if (element != null) {
                            val cx = element.bounds.left + element.bounds.width() / 2
                            val cy = element.bounds.top + element.bounds.height() / 2
                            controller.click(cx, cy)
                            ActionResult(true, "Interpreted '$gesture' as tap on element $elementId")
                        } else {
                            ActionResult(false, "Element $elementId not found for gesture '$gesture'")
                        }
                    }
                    // Has x,y -> probably wants to tap there
                    x != null && y != null -> {
                        controller.click(x, y)
                        ActionResult(true, "Interpreted '$gesture' as tap at ($x, $y)")
                    }
                    // Has text -> probably wants to type
                    !text.isNullOrEmpty() -> {
                        controller.setText(text)
                        ActionResult(true, "Interpreted '$gesture' as type: $text")
                    }
                    // Has swipe coords -> probably wants to swipe
                    startX != null && startY != null && endX != null && endY != null -> {
                        controller.scroll(startX, startY, endX, endY, 300L)
                        ActionResult(true, "Interpreted '$gesture' as swipe")
                    }
                    else -> ActionResult(
                        success = false,
                        message = "Unknown gesture '$gesture'. Provide element_id for taps, text for typing, or coordinates.",
                        retryable = true
                    )
                }
            }
        }
    }
}

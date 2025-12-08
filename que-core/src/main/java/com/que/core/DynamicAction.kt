package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * DYNAMIC ACTION SYSTEM
 * 
 * Instead of hardcoded Action types, the AI can generate ANY action it wants.
 * The AI sends a JSON like:
 * {
 *   "thought": "I need to tap at coordinates where I see the button",
 *   "action": {
 *     "gesture": "tap",
 *     "x": 540,
 *     "y": 1200
 *   }
 * }
 * 
 * Or for text input:
 * {
 *   "action": {
 *     "gesture": "type",
 *     "text": "Hello World",
 *     "submit": true
 *   }
 * }
 * 
 * The DynamicActionInterpreter converts this to executable commands.
 */

/**
 * A completely dynamic action - AI defines the gesture and all parameters
 */
@Serializable
data class DynamicAction(
    val gesture: String,  // "tap", "swipe", "type", "scroll", or ANYTHING AI invents
    val params: Map<String, JsonElement> = emptyMap()
) {
    // Helper methods to extract typed parameters
    fun getInt(key: String, default: Int = 0): Int = 
        params[key]?.jsonPrimitive?.intOrNull ?: default
    
    fun getLong(key: String, default: Long = 0L): Long = 
        params[key]?.jsonPrimitive?.longOrNull ?: default
    
    fun getString(key: String, default: String = ""): String = 
        params[key]?.jsonPrimitive?.contentOrNull ?: default
    
    fun getBoolean(key: String, default: Boolean = false): Boolean = 
        params[key]?.jsonPrimitive?.booleanOrNull ?: default
    
    fun getFloat(key: String, default: Float = 0f): Float = 
        params[key]?.jsonPrimitive?.floatOrNull ?: default
}

/**
 * Result of executing a dynamic action
 */
data class DynamicActionResult(
    val success: Boolean,
    val message: String = "",
    val isDone: Boolean = false,
    val data: Map<String, String> = emptyMap()
)

/**
 * Parses AI-generated JSON into a DynamicAction
 */
object DynamicActionParser {
    
    fun parse(json: String): DynamicAction? {
        return try {
            val jsonElement = Json.parseToJsonElement(json)
            parseFromJson(jsonElement.jsonObject)
        } catch (e: Exception) {
            null
        }
    }
    
    fun parseFromJson(jsonObject: JsonObject): DynamicAction? {
        return try {
            val actionObj = jsonObject["action"]?.jsonObject ?: return null
            
            // The "gesture" field tells us what the AI wants to do
            // If not present, check for "type" (backward compatibility)
            val gesture = actionObj["gesture"]?.jsonPrimitive?.contentOrNull
                ?: actionObj["type"]?.jsonPrimitive?.contentOrNull
                ?: return null
            
            // All other fields become parameters
            val params = actionObj.filterKeys { it != "gesture" && it != "type" }
            
            DynamicAction(gesture, params)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Interface for platform-specific action execution
 * Implement this in que-actions for Android-specific behavior
 */
interface DynamicActionExecutor {
    suspend fun execute(action: DynamicAction): DynamicActionResult
}

/**
 * Base interpreter that handles common gestures
 * Platform-specific executors can extend this
 */
abstract class BaseDynamicActionInterpreter : DynamicActionExecutor {
    
    override suspend fun execute(action: DynamicAction): DynamicActionResult {
        return when (action.gesture.lowercase()) {
            // Touch gestures
            "tap", "click", "press" -> executeTap(action)
            "long_press", "longpress", "hold" -> executeLongPress(action)
            "double_tap", "doubletap" -> executeDoubleTap(action)
            
            // Swipe/Scroll gestures
            "swipe", "drag" -> executeSwipe(action)
            "scroll" -> executeScroll(action)
            "fling" -> executeFling(action)
            
            // Text input
            "type", "input", "write", "enter_text" -> executeType(action)
            "clear", "clear_text" -> executeClear(action)
            
            // Navigation
            "back", "go_back" -> executeBack(action)
            "home", "go_home" -> executeHome(action)
            "switch_app", "recent", "recents" -> executeSwitchApp(action)
            
            // App control
            "open_app", "launch", "start_app" -> executeOpenApp(action)
            "close_app", "kill_app" -> executeCloseApp(action)
            
            // Wait
            "wait", "sleep", "delay", "pause" -> executeWait(action)
            
            // Finish
            "finish", "done", "complete", "end" -> executeFinish(action)
            
            // System
            "screenshot", "take_screenshot" -> executeScreenshot(action)
            "speak", "say", "tts" -> executeSpeak(action)
            
            // File operations
            "write_file", "save" -> executeWriteFile(action)
            "read_file", "load" -> executeReadFile(action)
            "append_file" -> executeAppendFile(action)
            
            // If AI invents a new gesture, try to handle it generically
            else -> executeCustomGesture(action)
        }
    }
    
    // Abstract methods - implement in platform-specific executor
    protected abstract suspend fun executeTap(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeLongPress(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeDoubleTap(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeSwipe(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeScroll(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeFling(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeType(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeClear(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeBack(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeHome(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeSwitchApp(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeOpenApp(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeCloseApp(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeWait(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeFinish(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeScreenshot(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeSpeak(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeWriteFile(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeReadFile(action: DynamicAction): DynamicActionResult
    protected abstract suspend fun executeAppendFile(action: DynamicAction): DynamicActionResult
    
    /**
     * Handle AI-invented gestures that we don't recognize.
     * Try to interpret based on common parameter patterns.
     */
    protected open suspend fun executeCustomGesture(action: DynamicAction): DynamicActionResult {
        // If it has x,y coordinates, treat as tap
        if (action.params.containsKey("x") && action.params.containsKey("y")) {
            return executeTap(action)
        }
        
        // If it has start/end coordinates, treat as swipe
        if (action.params.containsKey("startX") || action.params.containsKey("start_x")) {
            return executeSwipe(action)
        }
        
        // If it has text, treat as type
        if (action.params.containsKey("text")) {
            return executeType(action)
        }
        
        // If it has app_name or appName, treat as open_app
        if (action.params.containsKey("app_name") || action.params.containsKey("appName")) {
            return executeOpenApp(action)
        }
        
        // Unknown gesture - return helpful error
        return DynamicActionResult(
            success = false,
            message = "Unknown gesture '${action.gesture}'. " +
                "Supported gestures: tap, swipe, type, scroll, back, home, open_app, wait, finish. " +
                "Or include coordinates (x,y) for custom taps."
        )
    }
}

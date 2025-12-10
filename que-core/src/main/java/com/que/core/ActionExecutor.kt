package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.boolean
import kotlin.reflect.KClass

/**
 * Represents all possible actions the agent can perform.
 * Ported from Blurr's v2/actions/Action.kt
 */
@Serializable
sealed class Action {
    @Serializable
    data class Tap(val elementId: Int) : Action()
    
    @Serializable
    data class LongPress(val elementId: Int) : Action()
    
    @Serializable
    data class Type(val text: String, val elementId: Int? = null, val pressEnter: Boolean = true) : Action()
    
    @Serializable
    data class Scroll(val direction: Direction, val pixels: Int = 500, val duration: Long = 500) : Action()
    
    @Serializable
    data object Enter : Action()
    
    @Serializable
    data object Back : Action()
    
    @Serializable
    data object Home : Action()
    
    @Serializable
    data object SwitchApp : Action()
    
    @Serializable
    data class OpenApp(val appName: String) : Action()
    
    @Serializable
    data class SearchGoogle(val query: String) : Action()
    
    @Serializable
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()
    
    @Serializable
    data class WriteFile(val fileName: String, val content: String) : Action()
    
    @Serializable
    data class ReadFile(val fileName: String) : Action()
    
    @Serializable
    data class AppendFile(val fileName: String, val content: String) : Action()
    
    @Serializable
    data class Speak(val text: String) : Action()

    // Advanced Touch
    @Serializable data class DoubleTap(val elementId: Int) : Action()
    @Serializable data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val duration: Long = 300) : Action()
    @Serializable data class Pinch(val centerX: Int, val centerY: Int, val scale: Float, val duration: Long = 500) : Action()
    @Serializable data class DragDrop(val sourceElementId: Int, val targetElementId: Int, val duration: Long = 500) : Action()

    // Smart Scrolling
    @Serializable data class ScrollToElement(val elementId: Int, val maxScrolls: Int = 10, val direction: Direction = Direction.DOWN) : Action()
    @Serializable data object ScrollToTop : Action()
    @Serializable data object ScrollToBottom : Action()
    @Serializable data class Fling(val direction: Direction, val velocity: Float = 3000f) : Action()

    // Text Operations
    @Serializable data class ClearText(val elementId: Int) : Action()
    @Serializable data class ReplaceText(val elementId: Int, val newText: String) : Action()
    @Serializable data object SelectAll : Action()
    @Serializable data object Copy : Action()
    @Serializable data object Paste : Action()

    // Smart Waiting
    @Serializable data class WaitForElement(val elementDescription: String, val timeoutMs: Long = 10000) : Action()
    @Serializable data class Wait(val durationMs: Long = 2000) : Action()
    @Serializable data class WaitForIdle(val timeoutMs: Long = 5000) : Action()

    // System Actions
    @Serializable data class TakeScreenshot(val fileName: String? = null) : Action()
    @Serializable data object CloseApp : Action()
    @Serializable data object OpenNotifications : Action()
    @Serializable data class SetVolume(val streamType: VolumeStream, val level: Int) : Action()
    
    // Clipboard
    @Serializable data class SetClipboard(val text: String) : Action()
    @Serializable data object GetClipboard : Action()
    
    @Serializable
    data class Custom(val name: String, val params: Map<String, String>) : Action()

    // ===== DATA-DRIVEN ACTION REGISTRY (Blurr-style) =====
    companion object {

        /** Describes a single parameter for an action */
        data class ParamSpec(
            val name: String,
            val type: KClass<*>,
            val description: String,
            val required: Boolean = true
        )

        /** Describes an action's schema and how to build it */
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        /** Single source of truth for all actions */
        private val allSpecs: Map<String, Spec> = mapOf(
            // Basic Touch
            "tap" to Spec("tap", "Tap the element with the given ID.", listOf(
                ParamSpec("element_id", Int::class, "The numeric ID of the element to tap.")
            )) { args -> Tap(args["element_id"] as Int) },

            "long_press" to Spec("long_press", "Long press the element.", listOf(
                ParamSpec("element_id", Int::class, "The ID of the element.")
            )) { args -> LongPress(args["element_id"] as Int) },

            "double_tap" to Spec("double_tap", "Double tap the element.", listOf(
                ParamSpec("element_id", Int::class, "The ID of the element.")
            )) { args -> DoubleTap(args["element_id"] as Int) },

            // Text Input
            "type" to Spec("type", "Type text into the focused input field.", listOf(
                ParamSpec("text", String::class, "The text to type."),
                ParamSpec("press_enter", Boolean::class, "Whether to press Enter after typing.", false)
            )) { args -> Type(args["text"] as String, pressEnter = args["press_enter"] as? Boolean ?: true) },

            // Scrolling
            "scroll" to Spec("scroll", "Scroll in a direction.", listOf(
                ParamSpec("direction", String::class, "One of: up, down, left, right."),
                ParamSpec("pixels", Int::class, "Amount to scroll in pixels.", false)
            )) { args ->
                val dir = when ((args["direction"] as? String)?.lowercase()) {
                    "up" -> Direction.UP
                    "left" -> Direction.LEFT
                    "right" -> Direction.RIGHT
                    else -> Direction.DOWN
                }
                Scroll(dir, args["pixels"] as? Int ?: 500)
            },

            "scroll_to_element" to Spec("scroll_to_element", "Scroll until the element is visible.", listOf(
                ParamSpec("element_id", Int::class, "The ID of the element to find."),
                ParamSpec("max_scrolls", Int::class, "Maximum scroll attempts.", false)
            )) { args -> ScrollToElement(args["element_id"] as Int, args["max_scrolls"] as? Int ?: 10) },

            "scroll_to_top" to Spec("scroll_to_top", "Scroll to the top of the page.", emptyList()) { ScrollToTop },
            "scroll_to_bottom" to Spec("scroll_to_bottom", "Scroll to the bottom of the page.", emptyList()) { ScrollToBottom },

            // Navigation
            "back" to Spec("back", "Go back to the previous screen.", emptyList()) { Back },
            "home" to Spec("home", "Go to the home screen.", emptyList()) { Home },
            "switch_app" to Spec("switch_app", "Open the app switcher.", emptyList()) { SwitchApp },

            // App Control
            "open_app" to Spec("open_app", "Open the app by name or package.", listOf(
                ParamSpec("app_name", String::class, "The name or package of the app.")
            )) { args -> OpenApp(args["app_name"] as String) },

            "close_app" to Spec("close_app", "Close the current app.", emptyList()) { CloseApp },

            // Wait
            "wait" to Spec("wait", "Wait for a specified duration.", listOf(
                ParamSpec("duration", Long::class, "Time to wait in milliseconds.", false)
            )) { args -> Wait(args["duration"] as? Long ?: 2000L) },

            "wait_for_idle" to Spec("wait_for_idle", "Wait until UI stops changing.", listOf(
                ParamSpec("timeout_ms", Long::class, "Max wait time in milliseconds.", false)
            )) { args -> WaitForIdle(args["timeout_ms"] as? Long ?: 5000L) },

            // Text Operations
            "clear_text" to Spec("clear_text", "Clear text from an element.", listOf(
                ParamSpec("element_id", Int::class, "The ID of the element.")
            )) { args -> ClearText(args["element_id"] as Int) },

            "replace_text" to Spec("replace_text", "Replace text in an element.", listOf(
                ParamSpec("element_id", Int::class, "The ID of the element."),
                ParamSpec("new_text", String::class, "The new text.")
            )) { args -> ReplaceText(args["element_id"] as Int, args["new_text"] as String) },

            "copy" to Spec("copy", "Copy selected text to clipboard.", emptyList()) { Copy },
            "paste" to Spec("paste", "Paste text from clipboard.", emptyList()) { Paste },

            // System
            "take_screenshot" to Spec("take_screenshot", "Take a screenshot.", emptyList()) { TakeScreenshot() },
            "open_notifications" to Spec("open_notifications", "Open the notification shade.", emptyList()) { OpenNotifications },

            // Clipboard
            "set_clipboard" to Spec("set_clipboard", "Set clipboard content.", listOf(
                ParamSpec("text", String::class, "Text to copy to clipboard.")
            )) { args -> SetClipboard(args["text"] as String) },

            "get_clipboard" to Spec("get_clipboard", "Get clipboard content.", emptyList()) { GetClipboard },

            // File System
            "write_file" to Spec("write_file", "Write content to a file.", listOf(
                ParamSpec("file_name", String::class, "The file name."),
                ParamSpec("content", String::class, "The content to write.")
            )) { args -> WriteFile(args["file_name"] as String, args["content"] as String) },

            "read_file" to Spec("read_file", "Read content from a file.", listOf(
                ParamSpec("file_name", String::class, "The file name.")
            )) { args -> ReadFile(args["file_name"] as String) },

            "append_file" to Spec("append_file", "Append content to a file.", listOf(
                ParamSpec("file_name", String::class, "The file name."),
                ParamSpec("content", String::class, "The content to append.")
            )) { args -> AppendFile(args["file_name"] as String, args["content"] as String) },

            // Speech
            "speak" to Spec("speak", "Speak text aloud using TTS.", listOf(
                ParamSpec("text", String::class, "The text to speak.")
            )) { args -> Speak(args["text"] as String) },

            // Intents
            "launch_intent" to Spec("launch_intent", "Launch a system intent.", listOf(
                ParamSpec("intent_name", String::class, "The intent name (dial, view_url, share)."),
                ParamSpec("parameters", Map::class, "Key-value parameters for the intent.")
            )) { args ->
                @Suppress("UNCHECKED_CAST")
                LaunchIntent(args["intent_name"] as String, args["parameters"] as? Map<String, String> ?: emptyMap())
            },

            "search_google" to Spec("search_google", "Search Google.", listOf(
                ParamSpec("query", String::class, "The search query.")
            )) { args -> SearchGoogle(args["query"] as String) },

            // Swipe
            "swipe" to Spec("swipe", "Perform a swipe gesture.", listOf(
                ParamSpec("start_x", Int::class, "Starting X coordinate."),
                ParamSpec("start_y", Int::class, "Starting Y coordinate."),
                ParamSpec("end_x", Int::class, "Ending X coordinate."),
                ParamSpec("end_y", Int::class, "Ending Y coordinate."),
                ParamSpec("duration", Long::class, "Duration in milliseconds.", false)
            )) { args ->
                Swipe(
                    args["start_x"] as Int,
                    args["start_y"] as Int,
                    args["end_x"] as Int,
                    args["end_y"] as Int,
                    args["duration"] as? Long ?: 300L
                )
            },

            // Finish
            "finish" to Spec("finish", "Mark the task as complete.", listOf(
                ParamSpec("result", String::class, "Description of the result.", false)
            )) { args -> Custom("finish", mapOf("result" to (args["result"] as? String ?: "Task completed"))) },

            "done" to Spec("done", "Mark the task as complete.", listOf(
                ParamSpec("success", Boolean::class, "Whether the task succeeded."),
                ParamSpec("text", String::class, "Final message for the user.")
            )) { args -> Custom("finish", mapOf("success" to args["success"].toString(), "text" to (args["text"] as? String ?: ""))) }
        )

        /** Get a spec by action name */
        fun getSpec(name: String): Spec? = allSpecs[name]

        /** Get all action specs (for prompt generation) */
        fun getAllSpecs(): Collection<Spec> = allSpecs.values
    }
}

@Serializable
enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

@Serializable
enum class VolumeStream {
    MUSIC, RING, ALARM, NOTIFICATION
}

/**
 * Result of an action execution
 */
@Serializable
data class ActionResult(
    val success: Boolean,
    val message: String = "",
    val isDone: Boolean = false,  // Indicates task completion
    val data: Map<String, String> = emptyMap(),  // Additional result data
    val retryable: Boolean = false,
    val fatal: Boolean = false
)

/**
 * Interface for executing actions on the device
 */
interface ActionExecutor {
    suspend fun execute(action: Action): ActionResult
}

/**
 * Shared parser for Actions from JSON - Data-Driven (Blurr-style)
 */
object ActionParser {
    fun parse(actionObj: JsonObject): Action? {
        try {
            // Support both "type" (old format) and "gesture" (new dynamic format)
            val type = actionObj["type"]?.jsonPrimitive?.content 
                ?: actionObj["gesture"]?.jsonPrimitive?.content 
                ?: return null
            
            // Use the data-driven spec registry
            val spec = Action.getSpec(type)
            
            if (spec != null) {
                val args = mutableMapOf<String, Any?>()
                
                for (param in spec.params) {
                    val jsonValue = actionObj[param.name] ?: actionObj[snakeToCamel(param.name)]
                    if (jsonValue == null) {
                        if (param.required) continue // Allow missing optional params
                        continue
                    }
                    
                    args[param.name] = when (param.type) {
                        Int::class -> jsonValue.jsonPrimitive.intOrNull
                        String::class -> jsonValue.jsonPrimitive.contentOrNull
                        Long::class -> jsonValue.jsonPrimitive.longOrNull
                        Boolean::class -> jsonValue.jsonPrimitive.boolean
                        Map::class -> jsonValue.jsonObject.mapValues { it.value.jsonPrimitive.content }
                        else -> null
                    }
                }
                
                return spec.build(args)
            }
            
            // Fallback for legacy action names (navigation, etc.)
            return when (type) {
                "navigation" -> {
                    val direction = actionObj["direction"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "back"
                    when (direction) {
                        "back" -> Action.Back
                        "home" -> Action.Home
                        "enter" -> Action.Enter
                        "switch_app" -> Action.SwitchApp
                        else -> Action.Back
                    }
                }
                // Handle synonyms
                "click", "press" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull 
                        ?: actionObj["element_id"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.Tap(id)
                }
                "input", "enter_text", "write" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.Type(text)
                }
                "swipe" -> {
                    val dir = actionObj["direction"]?.jsonPrimitive?.contentOrNull
                    if (dir != null) {
                        // Swipe as scroll synonym
                        val direction = when (dir.lowercase()) {
                            "up" -> Direction.UP
                            "left" -> Direction.LEFT
                            "right" -> Direction.RIGHT
                            else -> Direction.DOWN
                        }
                        Action.Scroll(direction)
                    } else {
                        // Swipe with coordinates
                        val startX = actionObj["startX"]?.jsonPrimitive?.intOrNull ?: actionObj["start_x"]?.jsonPrimitive?.intOrNull ?: 0
                        val startY = actionObj["startY"]?.jsonPrimitive?.intOrNull ?: actionObj["start_y"]?.jsonPrimitive?.intOrNull ?: 0
                        val endX = actionObj["endX"]?.jsonPrimitive?.intOrNull ?: actionObj["end_x"]?.jsonPrimitive?.intOrNull ?: 0
                        val endY = actionObj["endY"]?.jsonPrimitive?.intOrNull ?: actionObj["end_y"]?.jsonPrimitive?.intOrNull ?: 0
                        val duration = actionObj["duration"]?.jsonPrimitive?.longOrNull ?: 300L
                        Action.Swipe(startX, startY, endX, endY, duration)
                    }
                }
                "sleep", "delay", "pause" -> {
                    val duration = actionObj["duration"]?.jsonPrimitive?.longOrNull ?: 2000L
                    Action.Wait(duration)
                }
                "stop", "exit" -> Action.Custom("finish", emptyMap())
                else -> {
                    // HYBRID FALLBACK: Return DynamicFallback action with all params
                    // This allows the executor to try dynamic interpretation
                    val allParams = actionObj.filterKeys { it != "gesture" && it != "type" }
                        .mapValues { it.value.jsonPrimitive.contentOrNull ?: "" }
                    Action.Custom("__dynamic__:$type", allParams)
                }
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    /** Convert snake_case to camelCase for parameter name flexibility */
    private fun snakeToCamel(snake: String): String {
        return snake.split("_").mapIndexed { index, s ->
            if (index == 0) s else s.replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }
}

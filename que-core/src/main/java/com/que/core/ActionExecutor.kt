package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

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
 * Shared parser for Actions from JSON
 */
object ActionParser {
    fun parse(actionObj: JsonObject): Action? {
        try {
            val type = actionObj["type"]?.jsonPrimitive?.content ?: return null
            
            return when (type) {
                "tap" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.Tap(id)
                }
                "type" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.Type(text)
                }
                "scroll" -> {
                    val dir = actionObj["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
                    val direction = if (dir == "up") Direction.UP else Direction.DOWN
                    Action.Scroll(direction)
                }
                "long_press" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.LongPress(id)
                }
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
                "back" -> Action.Back
                "home" -> Action.Home
                "switch_app" -> Action.SwitchApp
                "open_app" -> {
                    val appName = actionObj["appName"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.OpenApp(appName)
                }
                "search_google" -> {
                    val query = actionObj["query"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.SearchGoogle(query)
                }
                "launch_intent" -> {
                    val intentName = actionObj["intentName"]?.jsonPrimitive?.contentOrNull ?: return null
                    val params = actionObj["parameters"]?.jsonObject?.entries?.associate {
                        it.key to (it.value.jsonPrimitive.contentOrNull ?: "")
                    } ?: emptyMap()
                    Action.LaunchIntent(intentName, params)
                }
                "speak" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.Speak(text)
                }
                "wait" -> {
                    val duration = actionObj["duration"]?.jsonPrimitive?.longOrNull ?: 2000L
                    Action.Wait(duration)
                }
                "wait_for_element" -> {
                    val desc = actionObj["elementDescription"]?.jsonPrimitive?.contentOrNull ?: ""
                    val timeout = actionObj["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 10000L
                    Action.WaitForElement(desc, timeout)
                }
                "wait_for_idle" -> {
                    val timeout = actionObj["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 5000L
                    Action.WaitForIdle(timeout)
                }
                "double_tap" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.DoubleTap(id)
                }
                "swipe" -> {
                    val startX = actionObj["startX"]?.jsonPrimitive?.intOrNull ?: 0
                    val startY = actionObj["startY"]?.jsonPrimitive?.intOrNull ?: 0
                    val endX = actionObj["endX"]?.jsonPrimitive?.intOrNull ?: 0
                    val endY = actionObj["endY"]?.jsonPrimitive?.intOrNull ?: 0
                    val duration = actionObj["duration"]?.jsonPrimitive?.longOrNull ?: 300L
                    Action.Swipe(startX, startY, endX, endY, duration)
                }
                "scroll_to_element" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    val max = actionObj["maxScrolls"]?.jsonPrimitive?.intOrNull ?: 10
                    Action.ScrollToElement(id, max)
                }
                "scroll_to_top" -> Action.ScrollToTop
                "scroll_to_bottom" -> Action.ScrollToBottom
                "clear_text" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.ClearText(id)
                }
                "replace_text" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    val newText = actionObj["newText"]?.jsonPrimitive?.contentOrNull ?: ""
                    Action.ReplaceText(id, newText)
                }
                "select_all" -> Action.SelectAll
                "copy" -> Action.Copy
                "paste" -> Action.Paste
                "take_screenshot" -> {
                    val fileName = actionObj["fileName"]?.jsonPrimitive?.contentOrNull
                    Action.TakeScreenshot(fileName)
                }
                "close_app" -> Action.CloseApp
                "open_notifications" -> Action.OpenNotifications
                "set_clipboard" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    Action.SetClipboard(text)
                }
                "get_clipboard" -> Action.GetClipboard
                "write_file" -> {
                    val fileName = actionObj["fileName"]?.jsonPrimitive?.contentOrNull ?: return null
                    val content = actionObj["content"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.WriteFile(fileName, content)
                }
                "read_file" -> {
                    val fileName = actionObj["fileName"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.ReadFile(fileName)
                }
                "finish" -> {
                    Action.Custom("finish", emptyMap())
                }
                else -> Action.Custom(type, emptyMap())
            }
        } catch (e: Exception) {
            return null
        }
    }
}

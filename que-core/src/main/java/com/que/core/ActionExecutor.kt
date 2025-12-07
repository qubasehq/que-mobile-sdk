package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
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
    
    @Serializable
    data class Custom(val name: String, val params: Map<String, String>) : Action()
}

@Serializable
enum class Direction {
    UP, DOWN, LEFT, RIGHT
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

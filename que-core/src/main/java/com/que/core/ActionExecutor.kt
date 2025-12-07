package com.que.core

import kotlinx.serialization.Serializable

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
    val data: Map<String, String> = emptyMap()  // Additional result data
)

/**
 * Interface for executing actions on the device
 */
interface ActionExecutor {
    suspend fun execute(action: Action): ActionResult
}

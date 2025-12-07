package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Interface for reporting feedback from the user.
 */
interface UserFeedback {
    suspend fun reportError(
        step: AgentStepHistory,
        correctAction: Action,
        explanation: String
    )
    
    suspend fun reportSuccess(
        step: AgentStepHistory,
        rating: Int // 1-5
    )
}

/**
 * Stores details about a correction.
 */
@Serializable
data class Correction(
    val explanation: String,
    val incorrectAction: Action?,
    val correctAction: Action,
    val timestamp: Long
)

/**
 * Learns from user feedback to improve future performance.
 */
class AdaptiveLearningSystem(
    private val memory: ContextualMemory,
    private val llm: LLMClient
) : UserFeedback {
    
    override suspend fun reportError(
        step: AgentStepHistory,
        correctAction: Action,
        explanation: String
    ) {
        // Store correction
        val correction = Correction(
            explanation = explanation,
            incorrectAction = step.modelOutput?.actions?.firstOrNull(),
            correctAction = correctAction,
            timestamp = System.currentTimeMillis()
        )
        
        // Save to contextual memory associated with this app/activity
        memory.remember(
            key = "correction_${step.screenState.activityName}_${System.currentTimeMillis()}",
            value = Json.encodeToString(correction),
            context = MemoryContext(app = step.screenState.activityName)
        )
    }
    
    override suspend fun reportSuccess(step: AgentStepHistory, rating: Int) {
        if (rating >= 4) {
            // Reinforce this pattern
            val key = "success_pattern_${step.screenState.activityName}_${System.currentTimeMillis()}"
            val value = "Successful action for '${step.modelOutput?.nextGoal}': ${step.modelOutput?.actions}"
            
            memory.remember(
                key = key,
                value = value,
                context = MemoryContext(app = step.screenState.activityName)
            )
        }
    }
    
    suspend fun generateImprovedPrompt(
        task: String,
        screen: ScreenSnapshot,
        history: List<AgentStepHistory>
    ): List<Message> {
        val messages = mutableListOf<Message>()
        
        // Retrieve relevant corrections
        val corrections = memory.recall(
            query = "correction ${screen.activityName}",
            context = MemoryContext(app = screen.activityName)
        )
        
        if (corrections.isNotEmpty()) {
            val feedback = buildString {
                appendLine("Consider these past corrections for this app:")
                corrections.take(3).forEach { mem ->
                     try {
                         // Attempt to decode if it is a structured correction
                         // Or just print the value if it's text
                         if (mem.value.startsWith("{")) {
                             // Simplified display of correction JSON
                             appendLine("- ${mem.value}") 
                         } else {
                             appendLine("- ${mem.value}")
                         }
                     } catch (e: Exception) {
                         appendLine("- ${mem.value}")
                     }
                }
            }
            messages.add(Message(Role.USER, feedback))
        }
        
        return messages
    }
}

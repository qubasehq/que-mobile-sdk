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
            query = "correction ${screen.activityName} $task",
            context = MemoryContext(app = screen.activityName)
        )
        
        // Also look for similar failed attempts in history
        val failedAttempts = history.filter { 
            !it.results.all { r -> r.success } 
        }
        
        if (corrections.isNotEmpty() || failedAttempts.isNotEmpty()) {
            val feedback = buildString {
                appendLine("Learn from past mistakes:")
                
                if (corrections.isNotEmpty()) {
                    appendLine("Past Corrections:")
                    corrections.take(2).forEach { mem ->
                        appendLine("• ${mem.value}")
                    }
                }
                
                if (failedAttempts.isNotEmpty()) {
                    appendLine("Recent Failed Attempts:")
                    failedAttempts.takeLast(2).forEach { step ->
                        appendLine("• Previously tried: ${step.modelOutput?.nextGoal} - Failed")
                    }
                }
            }
            messages.add(Message(Role.SYSTEM, feedback))
        }
        
        return messages
    }
}

package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull


/**
 * Represents a complete plan of actions to achieve a goal.
 */
data class ActionPlan(
    val goal: String,
    val steps: List<PlannedStep>,
    val confidence: Float,
    val estimatedDurationMs: Long
)

/**
 * A single step in the action plan.
 */
data class PlannedStep(
    val action: Action,
    val description: String,
    val expectedOutcome: String? = null,
    val verification: StepVerification? = null,
    val fallbackActions: List<Action> = emptyList()
)

/**
 * How to verify if a step succeeded.
 */
data class StepVerification(
    val type: VerificationType,
    val target: String // Element ID, Text content, Activity name, etc.
)

enum class VerificationType {
    ELEMENT_VISIBLE,
    ELEMENT_HIDDEN,
    TEXT_CONTAINS,
    ACTIVITY_CHANGED,
    NONE
}

/**
 * Generates action plans using the LLM.
 */
class PredictivePlanner(
    private val llm: LLMClient
) {
    suspend fun planAhead(
        task: String,
        screen: ScreenSnapshot,
        history: List<AgentStepHistory> = emptyList()
    ): ActionPlan {
        val historyContext = if (history.isNotEmpty()) {
            "Recent attempts:\n" + history.takeLast(3).joinToString("\n") { 
                "Step ${it.step}: ${it.modelOutput?.nextGoal} - ${if (it.results.all { r -> r.success }) "Success" else "Failed"}"
            }
        } else ""
        val prompt = buildString {
            appendLine("You are an expert mobile agent planning a sequence of actions.")
            appendLine("Task: $task")
            if (historyContext.isNotBlank()) {
                appendLine()
                appendLine(historyContext)
            }
            appendLine("Current Activity: ${screen.activityName}")
            appendLine()
            appendLine("Screen Elements:")
            appendLine(screen.simplifiedDescription)
            appendLine()
            appendLine("Create a plan with multiple steps to achieve the task.")
            appendLine("For each step, specify the action, expected outcome, and verification.")
            appendLine("Respond strictly in the following JSON format:")
            appendLine("""
                {
                  "goal": "The high-level goal",
                  "confidence": 0.9,
                  "estimatedDurationMs": 5000,
                  "steps": [
                    {
                      "action": {"type": "tap", "elementId": 1},
                      "description": "Tap the search button",
                      "expectedOutcome": "Search bar appears",
                      "verification": {"type": "ELEMENT_VISIBLE", "target": "Search..."}
                    }
                  ]
                }
            """.trimIndent())
        }
        
        try {
            val response = llm.generate(listOf(Message(Role.USER, prompt)))
            val text = response.json ?: response.text
             // Basic JSON parsing
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) throw Exception("No JSON found")
            val jsonContent = text.substring(jsonStart, jsonEnd + 1)
            
             // We need to manually parse this since we don't have a full Json parser for the complex structure handy
             // Or rather, we should trust the ActionParser for the inner action
            
            // For MVP, simplified parsing or fallback
            // In a real app, use kotlinx.serialization properly
            
            return parseActionPlan(jsonContent)
        } catch (e: Exception) {
            // Fallback to empty plan
            android.util.Log.e("PredictivePlanner", "Planning failed: ${e.message}", e)
            return ActionPlan("Fallback", emptyList(), 0.0f, 0)
        }
    }
    
    private fun parseActionPlan(json: String): ActionPlan {
        val kotlinxJson = Json { 
            ignoreUnknownKeys = true 
            isLenient = true
        }
        
        val root = kotlinxJson.parseToJsonElement(json).jsonObject
        
        val goal = root["goal"]?.jsonPrimitive?.content ?: "Unknown Goal"
        val confidence = root["confidence"]?.jsonPrimitive?.floatOrNull ?: 1.0f
        val duration = root["estimatedDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L
        
        val stepsArray = root["steps"]?.jsonArray
        val steps = stepsArray?.mapNotNull { stepElem ->
            val stepObj = stepElem.jsonObject
            val actionObj = stepObj["action"]?.jsonObject ?: return@mapNotNull null
            val action = ActionParser.parse(actionObj) ?: return@mapNotNull null
            
            val desc = stepObj["description"]?.jsonPrimitive?.content ?: ""
            val outcome = stepObj["expectedOutcome"]?.jsonPrimitive?.content
            
            val verObj = stepObj["verification"]?.jsonObject
            val verification = if (verObj != null) {
                val typeStr = verObj["type"]?.jsonPrimitive?.content ?: "NONE"
                val target = verObj["target"]?.jsonPrimitive?.content ?: ""
                val type = try { VerificationType.valueOf(typeStr) } catch(e:Exception) { VerificationType.NONE }
                StepVerification(type, target)
            } else null
            
            PlannedStep(action, desc, outcome, verification)
        } ?: emptyList()
        
        return ActionPlan(goal, steps, confidence, duration)
    }
}

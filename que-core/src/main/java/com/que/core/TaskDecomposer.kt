package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Result of task decomposition.
 */
@Serializable
data class DecomposedTask(
    val intent: String,
    val subtasks: List<String>,
    val parameters: Map<String, String> = emptyMap(),
    val app: String? = null
)

/**
 * Helper to break down complex instructions into simpler subtasks using LLM.
 */
class TaskDecomposer(private val llm: LLMClient) {
    
    suspend fun decompose(naturalLanguageTask: String): DecomposedTask {
        val prompt = """
            Break down this user request into clear, actionable subtasks:
            "$naturalLanguageTask"
            
            Return strictly JSON with this schema:
            {
              "intent": "overall goal",
              "subtasks": ["step 1", "step 2"],
              "parameters": {"key": "value"},
              "app": "target app name or null"
            }
        """.trimIndent()
        
        try {
            val response = llm.generate(listOf(Message(Role.USER, prompt)))
            val text = response.json ?: response.text
            
            // Extract JSON
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) throw Exception("No JSON found")
            val jsonContent = text.substring(jsonStart, jsonEnd + 1)
            
            val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
            return json.decodeFromString(jsonContent)
            
        } catch (e: Exception) {
            // Fallback: one single task
            return DecomposedTask(
                intent = naturalLanguageTask,
                subtasks = listOf(naturalLanguageTask)
            )
        }
    }
}

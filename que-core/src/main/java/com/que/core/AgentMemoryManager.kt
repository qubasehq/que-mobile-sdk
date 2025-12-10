package com.que.core

/**
 * Memory Manager for the agent loop.
 * Maintains conversation context with the LLM across multiple steps.
 * 
 * This is the key difference from the simple string-based history:
 * - Builds proper prompts with context
 * - Tracks model outputs and results
 * - Supports corrective feedback
 * - Manages conversation flow
 */
class AgentMemoryManager {
    
    private val messages = mutableListOf<Message>()
    private var systemPrompt: String = ""
    
    /**
     * Initialize with the initial task.
     */
    fun addTask(task: String, systemPrompt: String) {
        this.systemPrompt = systemPrompt
        messages.clear()
        messages.add(Message(Role.SYSTEM, systemPrompt))
        messages.add(Message(Role.USER, "Task: $task"))
    }
    
    /**
     * Add a state message combining:
     * - Previous model output
     * - Previous action results
     * - Current screen state
     * - Step information
     */
    fun addStateMessage(
        modelOutput: AgentOutput?,
        results: List<ActionResult>,

        stepInfo: StepInfo,
        screen: ScreenSnapshot,
        history: List<AgentStepHistory> = emptyList(),
        relevantMemories: List<Memory> = emptyList()
    ) {
        val messageText = buildString {
            appendLine("=== $stepInfo ===")
            appendLine()

            // Relevant Memories
            if (relevantMemories.isNotEmpty()) {
                appendLine("**Contextual Memory:**")
                relevantMemories.forEach { memory ->
                    appendLine("- ${memory.value}")
                }
                appendLine()
            }

            // Summarize recent history (Last 5 steps)
            if (history.isNotEmpty()) {
                appendLine("**Recent History (Last 5 Steps):**")
                history.takeLast(5).forEach { step ->
                    val actionSummary = step.modelOutput?.actions?.joinToString(", ") { it.javaClass.simpleName } ?: "None"
                    val success = step.results.all { it.success }
                    appendLine("Step ${step.step}: $actionSummary → ${if (success) "Success" else "Failed"}")
                }
                appendLine("Learn from these results.")
                appendLine()
            }
            
            // Previous step results (if any)
            if (modelOutput != null && results.isNotEmpty()) {
                appendLine("**Previous Step Results:**")
                appendLine("Thought: ${modelOutput.thought}")
                appendLine("Goal: ${modelOutput.nextGoal}")
                appendLine()
                appendLine("Actions executed:")
                results.forEachIndexed { index, result ->
                    val action = modelOutput.actions.getOrNull(index)
                    appendLine("${index + 1}. ${action?.javaClass?.simpleName ?: "Unknown"}")
                    appendLine("   Result: ${if (result.success) "✓" else "✗"} ${result.message}")
                    if (result.data.isNotEmpty()) {
                        appendLine("   Data: ${result.data}")
                    }
                }
                appendLine()
            }
            
            // Current screen state
            appendLine("**Current Screen:**")
            appendLine("Activity: ${screen.activityName}")
            appendLine()
            appendLine("UI Elements:")
            appendLine(screen.simplifiedDescription)
            appendLine()
            
            // Instructions
            appendLine("**What to do next:**")
            appendLine("Analyze the current screen and decide the next action(s) to take.")
            appendLine("You can return multiple actions to execute in sequence.")
            appendLine("Respond with JSON in this format:")
            appendLine("""
                {
                  "thought": "your reasoning",
                  "nextGoal": "immediate goal",
                  "confidence": 0.9,
                  "actions": [
                  "actions": [
                    {"type": "tap", "elementId": 1},
                    {"type": "type", "text": "hello", "pressEnter": true},
                    {"type": "scroll", "direction": "down", "pixels": 500}
                  ]
                }
            """.trimIndent())
        }
        
        messages.add(Message(Role.USER, messageText))
    }
    
    /**
     * Add a correction note when LLM produces invalid output.
     */
    fun addCorrectionNote(note: String) {
        messages.add(Message(Role.SYSTEM, "⚠️ CORRECTION: $note"))
    }
    
    /**
     * Get all messages for LLM generation.
     */
    fun getMessages(): List<Message> = messages.toList()
    
    /**
     * Clear all messages (for new task).
     */
    fun clear() {
        messages.clear()
    }
    
    /**
     * Get conversation size (for debugging/monitoring).
     */
    fun getConversationSize(): Int = messages.size
    
    /**
     * Restore messages from checkpoint
     */
    fun restoreMessages(savedMessages: List<Message>) {
        messages.clear()
        messages.addAll(savedMessages)
    }
}

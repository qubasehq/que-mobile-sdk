package com.que.core

/**
 * Builds the detailed user message for the LLM, aggregating state from all sources.
 */
object UserMessageBuilder {

    data class Args(
        val task: String,
        val screen: ScreenSnapshot,
        val fileSystem: FileSystem,
        val history: List<String>,
        val stepInfo: String,
        val maxUiRepresentationLength: Int = 40000
    )

    fun build(args: Args): String {
        return buildString {
            appendLine("<agent_history>")
            if (args.history.isEmpty()) {
                appendLine("No history yet.")
            } else {
                args.history.forEach { appendLine(it) }
            }
            appendLine("</agent_history>")
            appendLine()

            appendLine("<agent_state>")
            appendLine("<user_request>")
            appendLine(args.task)
            appendLine("</user_request>")
            
            appendLine("<file_system>")
            appendLine(args.fileSystem.describe())
            appendLine("</file_system>")
            
            appendLine("<todo_contents>")
            val todo = args.fileSystem.getTodoContents()
            appendLine(if (todo.isBlank()) "[Current todo.md is empty]" else todo)
            appendLine("</todo_contents>")
            
            // Sensitive data handling can be injected here in the future
            
            appendLine("<step_info>")
            appendLine(args.stepInfo)
            appendLine("</step_info>")
            appendLine("</agent_state>")
            appendLine()

            appendLine("<android_state>")
            appendLine("Current Activity: ${args.screen.activityName}")
            
            val uiDesc = args.screen.simplifiedDescription
            if (uiDesc.length > args.maxUiRepresentationLength) {
                appendLine("Visible elements (truncated to ${args.maxUiRepresentationLength} chars):")
                appendLine(uiDesc.take(args.maxUiRepresentationLength))
            } else {
                appendLine("Visible elements:")
                appendLine(uiDesc)
            }
            appendLine("</android_state>")
        }
    }
}

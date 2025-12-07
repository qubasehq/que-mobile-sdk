package com.que.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Base64

enum class TraceFormat {
    JSON, HTML
}

/**
 * Tool for debugging agent execution.
 */
class AgentDebugger(
    private val agent: QueAgent
) {
    
    fun exportTrace(format: TraceFormat): String {
        return when (format) {
            TraceFormat.JSON -> exportAsJson()
            TraceFormat.HTML -> exportAsHtmlReport()
        }
    }
    
    private fun exportAsJson(): String {
        val history = agent.getHistory()
        val json = Json { prettyPrint = true }
        return json.encodeToString(history)
    }
    
    private fun exportAsHtmlReport(): String {
        val history = agent.getHistory()
        
        return buildString {
            appendLine("<!DOCTYPE html><html><head>")
            appendLine("<meta charset='UTF-8'>")
            appendLine("<style>")
            appendLine("body { font-family: sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }")
            appendLine(".step { border: 1px solid #ccc; margin: 10px 0; padding: 15px; border-radius: 8px; }")
            appendLine(".screenshot { max-width: 100%; border: 1px solid #ddd; margin-top: 10px; }")
            appendLine(".success { color: green; font-weight: bold; }")
            appendLine(".failure { color: red; font-weight: bold; }")
            appendLine("h1, h2 { color: #333; }")
            appendLine("pre { background: #f5f5f5; padding: 10px; overflow-x: auto; }")
            appendLine("</style></head><body>")
            
            appendLine("<h1>Agent Execution Trace</h1>")
            appendLine("<p>Total Steps: ${history.size}</p>")
            
            history.forEachIndexed { index, step ->
                appendLine("<div class='step'>")
                appendLine("<h2>Step ${index + 1}</h2>")
                appendLine("<p><strong>Thought:</strong> ${step.modelOutput?.thought ?: "N/A"}</p>")
                appendLine("<p><strong>Goal:</strong> ${step.modelOutput?.nextGoal ?: "N/A"}</p>")
                
                if (step.screenState.screenshot != null) {
                    val base64 = Base64.encodeToString(step.screenState.screenshot, Base64.NO_WRAP)
                    appendLine("<img class='screenshot' src='data:image/jpeg;base64,$base64' />")
                }
                
                appendLine("<h3>Screen Description:</h3>")
                appendLine("<pre>${step.screenState.simplifiedDescription}</pre>")
                
                appendLine("<h3>Actions:</h3>")
                step.results.forEachIndexed { i, result ->
                    val cls = if (result.success) "success" else "failure"
                    val action = step.modelOutput?.actions?.getOrNull(i)
                    appendLine("<div class='$cls'>")
                    appendLine("<p>${i + 1}. $action</p>")
                    appendLine("<p>→ ${result.message}</p>")
                    appendLine("</div>")
                }
                
                if (step.failureCount > 0) {
                     appendLine("<p class='failure'>Failures in this step: ${step.failureCount}</p>")
                }
                
                appendLine("</div>")
            }
            
            appendLine("</body></html>")
        }
    }
}

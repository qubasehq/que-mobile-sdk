package com.que.vision

import com.que.core.service.InteractiveElement
import com.que.core.service.LLMClient
import com.que.core.service.Message
import com.que.core.service.Role
import com.que.core.service.ScreenSnapshot
import com.que.core.util.AgentLogger
import kotlinx.serialization.json.*

/**
 * Semantic Visual Verification (SVV)
 * 
 * Triggered when accessibility metadata is missing or ambiguous.
 * Uses Gemini 2.0 Flash Vision to "see" the screen and label elements.
 */
class SemanticVisualVerifier(
    private val llmClient: LLMClient
) {
    private val TAG = "SemanticVisualVerifier"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Periodically check if the current screen snapshot requires SVV.
     * Criteria: > 40% of interactive elements are unlabeled AND screen is not transient.
     */
    fun shouldTriggerSVV(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.interactiveElements.isEmpty()) return false
        
        val unlabeledCount = snapshot.interactiveElements.count { it.description.isBlank() }
        val ratio = unlabeledCount.toFloat() / snapshot.interactiveElements.size
        
        AgentLogger.d(TAG, "SVV Check: $unlabeledCount/${snapshot.interactiveElements.size} unlabeled (ratio: ${String.format("%.2f", ratio)})")
        
        // Trigger if more than 40% are unlabeled, or if there are 3+ unlabeled elements
        return ratio > 0.4f || unlabeledCount >= 3
    }

    /**
     * Performs vision analysis on the screenshot to label interactive elements.
     */
    suspend fun verify(snapshot: ScreenSnapshot, screenshot: ByteArray): ScreenSnapshot {
        AgentLogger.i(TAG, "🚀 Triggering Semantic Visual Verification (SVV)...")
        
        val unlabeledElements = snapshot.interactiveElements.filter { it.description.isBlank() }
        if (unlabeledElements.isEmpty()) return snapshot

        val prompt = buildVisionPrompt(unlabeledElements, snapshot.displayWidth, snapshot.displayHeight)
        
        try {
            val message = Message(
                role = Role.USER,
                content = prompt,
                images = mutableListOf(screenshot)
            )
            
            val response = llmClient.generate(listOf(message))
            val visionResults = parseVisionResponse(response.text)
            
            if (visionResults.isEmpty()) {
                AgentLogger.w(TAG, "SVV returned no valid labels")
                return snapshot
            }

            // Update elements with vision results
            val updatedElements = snapshot.interactiveElements.map { element ->
                val visionLabel = visionResults[element.id]
                if (visionLabel != null && element.description.isBlank()) {
                    AgentLogger.d(TAG, "SVV: Identified element [${element.id}] as '$visionLabel'")
                    element.copy(description = "👁️ $visionLabel")
                } else {
                    element
                }
            }

            // Update simplified description
            val updatedDescription = rebuildDescription(snapshot.simplifiedDescription, visionResults)

            return snapshot.copy(
                interactiveElements = updatedElements,
                simplifiedDescription = updatedDescription,
                ocrText = "SVV performed. Found ${visionResults.size} labels."
            )
        } catch (e: Exception) {
            AgentLogger.e(TAG, "SVV Failed", e)
            return snapshot
        }
    }

    private fun buildVisionPrompt(elements: List<InteractiveElement>, width: Int, height: Int): String {
        return """
            I am an AI assistant for Android. I have a screenshot and a list of interactive elements that lack text descriptions.
            Your task is to look at the screenshot and provide a concise (1-3 words) label for each element based on its visual appearance (icons, context, etc.).
            
            Screen Dimensions: ${width}x${height}
            Elements to label:
            ${elements.joinToString("\n") { "[${it.id}] at [${it.bounds.left}, ${it.bounds.top}, ${it.bounds.right}, ${it.bounds.bottom}]" }}
            
            Return ONLY a valid JSON object mapping ID to label. 
            Example: {"1": "Settings Icon", "4": "Search Button"}
            Do not include any other text or markdown formatting.
        """.trimIndent()
    }

    private fun parseVisionResponse(text: String): Map<Int, String> {
        return try {
            val cleaned = text.trim()
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            val jsonElement = json.parseToJsonElement(cleaned).jsonObject
            jsonElement.entries.associate { entry ->
                entry.key.toInt() to entry.value.jsonPrimitive.content
            }
        } catch (e: Exception) {
            AgentLogger.w(TAG, "Failed to parse SVV response: ${e.message}")
            emptyMap()
        }
    }

    private fun rebuildDescription(oldDescription: String, visionResults: Map<Int, String>): String {
        var newDescription = oldDescription
        visionResults.forEach { (id, label) ->
            // Use regex to find the line for this ID and append/replace description
            // Format in SemanticParser: "[$id]  <className> {resourceId} (clickable)"
            val regex = Regex("\\[$id\\]\\s+<")
            if (regex.containsMatchIn(newDescription)) {
                newDescription = newDescription.replace(regex, "[$id] 👁️ $label <")
            }
        }
        return newDescription
    }
}

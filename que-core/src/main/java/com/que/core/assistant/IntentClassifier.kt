package com.que.core.assistant

import com.que.core.service.LLMClient
import com.que.core.service.Message
import com.que.core.service.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Routes user utterances into CHAT or AUTOMATE intents via a fast LLM call.
 * This is the brain of the routing decision.
 */
class IntentClassifier(private val llm: LLMClient) {

    sealed class Intent {
        object Chat : Intent()
        data class Automate(val task: String) : Intent()
    }

    /**
     * Classifies a user utterance given the recent conversation history.
     *
     * @param utterance  The user's latest message.
     * @param history    Recent conversation turns (for context).
     * @return           CHAT or AUTOMATE(task).
     */
    suspend fun classify(
        utterance: String, 
        history: List<Message>,
        logCallback: (suspend (String) -> Unit)? = null
    ): Intent {
        val systemPrompt = """
You are an intent classifier for a voice AI assistant controlling an Android phone.

Classify the user's message into exactly one of two intents:
1. CHAT   — The user wants a conversational reply. Questions, small talk, 
             asking for information the AI can answer from knowledge, 
             asking what something is, asking for advice.
2. AUTOMATE — The user wants something DONE on their phone. Opening apps,
               sending messages, making calls, playing music, setting alarms,
               searching, navigating, changing settings, doing anything on the device.

Rules:
- "What is X?" → CHAT
- "Open X" / "Play X" / "Send X" / "Set X" → AUTOMATE
- "Tell me about X" → CHAT
- "Show me X on my phone" → AUTOMATE
- "How does X work?" → CHAT
- Ambiguous → CHAT (safer)

Respond ONLY with valid JSON. Nothing else. No explanation.
Format: {"intent":"CHAT"} or {"intent":"AUTOMATE","task":"<the exact task to perform>"}
The task field should be a clean, direct instruction for a phone automation agent.
        """.trimIndent()

        val messages = mutableListOf<Message>()
        messages.add(Message(Role.SYSTEM, systemPrompt))
        // Include last 3 turns for context (not full history — keep this fast)
        messages.addAll(history.takeLast(6))
        messages.add(Message(Role.USER, utterance))

        return try {
            logCallback?.invoke(">>>> [INTENT_CLASSIFIER] Calling LLM...")
            val response = llm.generate(messages)
            val responseText = response.json ?: response.text
            logCallback?.invoke(">>>> [INTENT_CLASSIFIER] LLM Output: $responseText")
            parseIntent(responseText, logCallback)
        } catch (e: Exception) {
            logCallback?.invoke(">>>> [INTENT_CLASSIFIER] THREW EXCEPTION: ${e.message}")
            // On failure, default to CHAT — never accidentally run automation
            Intent.Chat
        }
    }

    private suspend fun parseIntent(
        text: String,
        logCallback: (suspend (String) -> Unit)? = null
    ): Intent {
        // Find the first { and last } to extract just the JSON part
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        
        val cleaned = if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            text.substring(startIndex, endIndex + 1)
        } else {
            text.replace(Regex("```json|```"), "").trim()
        }
        
        logCallback?.invoke(">>>> [INTENT_CLASSIFIER] Cleaned JSON: $cleaned")
        
        return try {
            val kotlinxJson = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val obj = kotlinxJson.parseToJsonElement(cleaned).jsonObject

            when (obj["intent"]?.jsonPrimitive?.content) {
                "AUTOMATE" -> {
                    val task = obj["task"]?.jsonPrimitive?.content ?: ""
                    if (task.isBlank()) Intent.Chat else Intent.Automate(task)
                }
                else -> Intent.Chat
            }
        } catch (e: Exception) {
            android.util.Log.e("IntentClassifier", "Failed to parse intent JSON: '$text'", e)
            Intent.Chat
        }
    }
}

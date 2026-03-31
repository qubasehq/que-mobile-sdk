package com.que.core.assistant

import com.que.core.service.LLMClient
import com.que.core.service.Message
import com.que.core.service.Role
import com.que.core.util.AgentLogger

/**
 * Handles the pure chat path.
 * Calls LLM with the system prompt + conversation history + new user message.
 * Returns a short reply.
 */
class ConversationHandler(
    private val llm: LLMClient,
    private val memory: AssistantMemory,
    private val settings: AssistantSettings
) {

    /**
     * Generate a conversational reply to the user's utterance.
     * Automatically updates AssistantMemory with both turns.
     *
     * @param utterance The user's message.
     * @return The assistant's reply text.
     */
    suspend fun reply(utterance: String): String {
        memory.addUserTurn(utterance)

        val messages = mutableListOf<Message>()
        messages.add(Message(Role.SYSTEM, AssistantPromptBuilder.buildSystemPrompt()))
        messages.addAll(memory.getHistory())
        // Note: getHistory() already includes the utterance we just added above

        val response = try {
            val result = llm.generate(messages)
            val text = result.text.trim()
            if (text.isBlank()) "Sorry, I didn't catch that." else text
        } catch (e: Exception) {
            AgentLogger.e("ConversationHandler", "LLM call failed: ${e.message}", e)
            "Something went wrong on my end."
        }

        memory.addAssistantTurn(response)
        return response
    }

    /**
     * Inject a system note into memory without a user utterance.
     * Used to tell the LLM about automation results so it can refer to them
     * in future conversation turns.
     *
     * Example: "The automation task completed: opened Spotify and started playback."
     */
    fun injectContext(note: String) {
        // Store as a MODEL-flavored assistant turn so future replies reference it
        memory.addAssistantTurn("[Task completed: $note]")
    }
}

package com.que.core.assistant

import com.que.core.service.Message
import com.que.core.service.Role

/**
 * Rolling conversation history between user and assistant.
 * Separate from AgentMemoryManager (which is only for the agent loop).
 */
class AssistantMemory(private val maxTurns: Int = 20) {

    private val turns = ArrayDeque<Message>()

    fun addUserTurn(text: String) {
        turns.addLast(Message(Role.USER, text))
        trim()
    }

    fun addAssistantTurn(text: String) {
        turns.addLast(Message(Role.MODEL, text))
        trim()
    }

    /**
     * Returns the full message list ready to pass to LLMClient.generate().
     * Prepend the system prompt yourself before calling generate().
     */
    fun getHistory(): List<Message> = turns.toList()

    fun clear() = turns.clear()

    private fun trim() {
        // Each "turn" is one message. Keep at most maxTurns * 2 messages
        // (user + assistant pairs).
        while (turns.size > maxTurns * 2) {
            turns.removeFirst()
        }
    }
}

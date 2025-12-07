package com.que.core

/**
 * Abstraction for the Large Language Model.
 * Implementations can wrap Gemini, OpenAI, Claude, etc.
 */
interface LLMClient {
    /**
     * Generates a response from the LLM based on the provided messages.
     * @param messages The conversation history.
     * @return The LLM's response.
     */
    suspend fun generate(messages: List<Message>): LLMResponse
}

data class Message(
    val role: Role,
    val content: String,
    val images: MutableList<ByteArray> = mutableListOf()
)

enum class Role {
    USER, MODEL, SYSTEM
}

data class LLMResponse(
    val text: String,
    val json: String? = null // If the model returns structured JSON
)

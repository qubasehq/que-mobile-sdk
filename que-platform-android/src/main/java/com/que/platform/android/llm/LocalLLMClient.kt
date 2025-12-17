package com.que.platform.android.llm

import android.content.Context
import com.que.core.LLMClient
import com.que.core.LLMResponse
import com.que.core.Message

/**
 * Placeholder for Local LLM Client.
 * Dependencies have been removed temporarily.
 */
class LocalLLMClient(
    private val context: Context, 
    private val modelPath: String
) : LLMClient {

    override suspend fun generate(messages: List<Message>): LLMResponse {
        return LLMResponse("Local LLM is currently disabled.")
    }
    
    fun close() {
        // No-op
    }
}

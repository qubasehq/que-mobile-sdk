package com.que.platform.android.llm
import com.que.core.service.LLMClient
import com.que.core.service.LLMResponse
import com.que.core.service.Message

import android.content.Context

/**
 * Placeholder for Local LLM Client.
 * Dependencies have been removed temporarily.
 */
@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
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

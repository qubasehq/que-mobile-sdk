package com.que.llm

import com.que.core.LLMClient
import com.que.core.LLMResponse
import com.que.core.Message
import com.que.core.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-1.5-pro"
) : LLMClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
        isLenient = true
    }

    private val circuitBreaker = com.que.core.CircuitBreaker()
    private var lastRequestTime = 0L
    private val minRequestIntervalMs = 2000L // Cap at 30 RPM (Safety margin for 60 RPM limit)

    override suspend fun generate(messages: List<Message>): LLMResponse {
        return withContext(Dispatchers.IO) {
            // Apply Rate Limiting
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTime
            if (timeSinceLast < minRequestIntervalMs) {
                kotlinx.coroutines.delay(minRequestIntervalMs - timeSinceLast)
            }
            lastRequestTime = System.currentTimeMillis()

            circuitBreaker.execute {
                retryWithBackoff {
                    performRequest(messages)
                }
            }
        }
    }

    private suspend fun performRequest(messages: List<Message>): LLMResponse {
        try {
            val payload = buildRequest(messages)
            val requestBody = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw RuntimeException("Gemini API Error (${response.code}): $bodyString")
            }

            return parseResponse(bodyString)
        } catch (e: Exception) {
            // Unwrap if it's already a RuntimeException we threw
            if (e is RuntimeException && e.message?.startsWith("Gemini API Error") == true) throw e
            throw RuntimeException("LLM Generation Failed: ${e.message}", e)
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 2000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries - 1) { _ ->
            try {
                return block()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isRateLimit = msg.contains("429") || msg.contains("Too Many Requests")
                val isServerErr = msg.contains("500") || msg.contains("503")
                
                if (isRateLimit || isServerErr) {
                    // Log warning here if logger available
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong()
                } else {
                    throw e
                }
            }
        }
        return block() // Last attempt
    }

    private fun buildRequest(messages: List<Message>): GenerateContentRequest {
        val contents = messages.map { msg ->
            val parts = mutableListOf<Part>()
            
            // Add text part
            if (msg.content.isNotBlank()) {
                parts.add(Part(text = msg.content))
            }
            
            // Add image parts
            msg.images.forEach { imageBytes ->
                val base64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
                parts.add(Part(inlineData = InlineData("image/jpeg", base64)))
            }
            
            Content(
                role = if (msg.role == Role.MODEL) "model" else "user",
                parts = parts
            )
        }
        
        return GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.4f,
                maxOutputTokens = 1024
            )
        )
    }

    private fun parseResponse(bodyString: String): LLMResponse {
        val responseObj = json.decodeFromString<GenerateContentResponse>(bodyString)
        val firstCandidate = responseObj.candidates?.firstOrNull()
        val textContent = firstCandidate?.content?.parts?.firstOrNull()?.text ?: ""
        
        return LLMResponse(text = textContent, json = textContent)
    }

    @Serializable
    data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    data class Content(
        val role: String,
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null
    )

    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    data class GenerateContentResponse(val candidates: List<Candidate>?)

    @Serializable
    data class Candidate(
        val content: Content?,
        val finishReason: String?
    )
}

package com.que.llm
import com.que.core.internal.CircuitBreaker
import com.que.core.service.LLMClient
import com.que.core.service.LLMResponse
import com.que.core.service.Message
import com.que.core.service.Role

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
    private val model: String = "gemini-2.0-flash"
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

    // Increased threshold for 503 overload spikes
    private val circuitBreaker = CircuitBreaker(failureThreshold = 10, resetTimeout = 60000)
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

    override suspend fun listModels(): List<com.que.core.service.ModelInfo> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw RuntimeException("Gemini API Error (${response.code}): $bodyString")
            }

            val listResponse = json.decodeFromString<ListModelsResponse>(bodyString)
            listResponse.models?.map { 
                com.que.core.service.ModelInfo(
                    name = it.name.removePrefix("models/"),
                    displayName = it.displayName ?: it.name,
                    description = it.description ?: "",
                    supportedMethods = it.supportedGenerationMethods ?: emptyList()
                )
            } ?: emptyList()
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
        maxRetries: Int = 5,
        initialDelay: Long = 3000,
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
                // Low temperature (0.2) is critical for tool use/automation to prevent hallucinations
                // while still allowing the assistant to sound natural in chat.
                temperature = 0.2f,
                // Expanded token count to allow for potentially large JSON responses during complex automations
                maxOutputTokens = 2048,
                topP = 0.8f,
                topK = 40
            ),
            safetySettings = null,
            tools = null,
            systemInstruction = null
        )
    }

    private fun parseResponse(bodyString: String): LLMResponse {
        val responseObj = json.decodeFromString<GenerateContentResponse>(bodyString)
        val candidate = responseObj.candidates?.firstOrNull()
        
        val text = candidate
            ?.content
            ?.parts
            ?.firstOrNull { it.text != null }
            ?.text ?: ""
        
        return LLMResponse(
            text = text,
            json = null
        )
    }

    @Serializable
    data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
        val safetySettings: List<SafetySetting>? = null,
        val tools: List<Tool>? = null,
        val systemInstruction: Content? = null
    )

    @Serializable
    data class Content(
        val role: String,
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null,
        val fileData: FileData? = null,
        val functionCall: FunctionCall? = null,
        val functionResponse: FunctionResponse? = null
    )

    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String
    )

    @Serializable
    data class FileData(
        val mimeType: String,
        val fileUri: String
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val args: JsonObject
    )

    @Serializable
    data class FunctionResponse(
        val name: String,
        val response: JsonObject
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null,
        val topK: Int? = null,
        val topP: Float? = null,
        val stopSequences: List<String>? = null,
        val candidateCount: Int? = null,
        val presencePenalty: Float? = null,
        val frequencyPenalty: Float? = null,
        val responseMimeType: String? = null,
        val responseSchema: JsonObject? = null,
        val seed: Int? = null
    )
    
    @Serializable
    data class SafetySetting(
        val category: String,
        val threshold: String
    )

    @Serializable
    data class Tool(
        val functionDeclarations: List<FunctionDeclaration>
    )

    @Serializable
    data class FunctionDeclaration(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )

    @Serializable
    data class GenerateContentResponse(
        val candidates: List<Candidate>? = null,
        val promptFeedback: PromptFeedback? = null,
        val usageMetadata: UsageMetadata? = null
    )

    @Serializable
    data class PromptFeedback(
        val blockReason: String? = null
    )

    @Serializable
    data class UsageMetadata(
        val promptTokenCount: Int? = null,
        val candidatesTokenCount: Int? = null,
        val totalTokenCount: Int? = null
    )

    @Serializable
    data class Candidate(
        val content: Content?,
        val finishReason: String?
    )

    @Serializable
    data class ListModelsResponse(val models: List<GeminiModelInfo>?)

    @Serializable
    data class GeminiModelInfo(
        val name: String,
        val displayName: String? = null,
        val description: String? = null,
        val supportedGenerationMethods: List<String>? = null
    )
}

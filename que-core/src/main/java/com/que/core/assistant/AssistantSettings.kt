package com.que.core.assistant

import com.que.core.model.AgentSettings

/**
 * Configuration for QueAssistant.
 */
data class AssistantSettings(
    /** Operating mode: CHAT or ASSISTANT. */
    val mode: AssistantMode = AssistantMode.ASSISTANT,

    /** Whether to pipe replies through SpeechService. */
    val enableSpeech: Boolean = true,

    /** Whether to enable debug logging. */
    val enableLogging: Boolean = false,

    /** How many conversation turns to keep in memory. */
    val conversationWindowSize: Int = 20,

    /** Settings forwarded to QueAgent when running automation. */
    val agentSettings: AgentSettings = AgentSettings(isAutonomousMode = true),

    /** Optional wake word (e.g. "hey que"). null = no wake word. */
    val wakeWord: String? = null,

    /** Voice locale for STT/TTS. */
    val voiceLocale: String = "en-IN",

    /** Specific voice name to use (e.g. "en-us-x-sfg-local"). null = system default. */
    val voiceName: String? = null,

    /** Enables the Jarvis-style system prompt for conversation. */
    val jarvisPersonality: Boolean = true
)

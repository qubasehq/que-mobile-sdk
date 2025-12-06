package com.que.core

/**
 * Interface for speech feedback.
 * Allows the agent to speak to the user without depending on Android APIs directly.
 */
interface SpeechService {
    fun speak(text: String)
}

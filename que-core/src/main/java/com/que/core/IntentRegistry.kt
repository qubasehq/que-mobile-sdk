package com.que.core

/**
 * Registry for Android Intents that can be launched by the agent.
 */
interface IntentRegistry {
    fun launch(intentName: String, parameters: Map<String, String>): Boolean
    fun listIntents(): List<String>
}

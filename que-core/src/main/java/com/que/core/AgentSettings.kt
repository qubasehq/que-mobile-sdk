package com.que.core

/**
 * Configuration settings for the agent's behavior.
 * Allows tuning agent parameters without code changes.
 */
data class AgentSettings(
    /**
     * Maximum number of steps the agent can take before stopping.
     * Prevents infinite loops.
     */
    val maxSteps: Int = 30,
    
    /**
     * Maximum number of retries for failed actions.
     */
    val maxRetries: Int = 3,
    
    /**
     * Maximum number of consecutive LLM failures before stopping.
     */
    val maxFailures: Int = 3,
    
    /**
     * Whether to enable verbose logging.
     */
    val enableLogging: Boolean = true,
    
    /**
     * LLM model to use for decision making.
     */
    val model: String = "gemini-2.5-flash",
    
    /**
     * Timeout for LLM generation in milliseconds.
     */
    val llmTimeoutMs: Long = 30000,
    
    /**
     * Whether to include screenshots in LLM prompts.
     */
    val includeScreenshots: Boolean = true,
    
    /**
     * Whether to retry failed actions automatically.
     */
    val retryFailedActions: Boolean = true,

    /**
     * Whether to enable predictive planning.
     */
    val enablePredictivePlanning: Boolean = false,
    
    /**
     * Whether to enable speech feedback (TTS).
     */
    val enableSpeech: Boolean = true
)

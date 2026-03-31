package com.que.core.assistant

/**
 * Events emitted by QueAssistant.
 * The UI layer collects this flow and reacts accordingly.
 */
sealed class AssistantEvent {

    // ── Lifecycle ──────────────────────────────────────────────
    object ThinkingStarted : AssistantEvent()
    object ThinkingEnded : AssistantEvent()
    object ListeningStarted : AssistantEvent()
    data class ListeningEnded(val transcript: String) : AssistantEvent()

    // ── Conversation ───────────────────────────────────────────
    /**
     * A reply from the assistant. speak=true → pipe through SpeechService.
     */
    data class SpeakResponse(val text: String, val speak: Boolean = true) : AssistantEvent()

    // ── Automation lifecycle ────────────────────────────────────
    /**
     * Emitted when the assistant decides to run automation.
     * UI: show "Doing that for you…" banner.
     */
    data class AgentStarted(val taskDescription: String) : AssistantEvent()

    /**
     * Live status update from QueAgent (maps from AgentEvent.Narration).
     * UI: update sub-label on banner.
     */
    data class AgentNarration(val message: String, val type: String) : AssistantEvent()

    /**
     * Agent needs user input. UI MUST render and call replyToAgent().
     * Maps from AgentEvent.UserQuestionAsked.
     */
    data class AgentQuestion(val question: String, val options: List<String>?) : AssistantEvent()

    /**
     * Agent needs confirmation. UI MUST render confirm/deny.
     * Maps from AgentEvent.ConfirmationRequired.
     */
    data class AgentConfirmation(val summary: String, val preview: String) : AssistantEvent()

    /** Automation succeeded. UI: hide banner, optionally speak summary. */
    data class AgentFinished(val summary: String) : AssistantEvent()

    /** Automation failed. UI: hide banner, show error. */
    data class AgentFailed(val reason: String) : AssistantEvent()

    // ── Errors ─────────────────────────────────────────────────
    data class Error(val message: String) : AssistantEvent()
}

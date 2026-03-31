package com.que.core.assistant

/**
 * Operating mode for QueAssistant.
 */
enum class AssistantMode {
    /** Only conversation. Never runs automation. */
    CHAT,
    /** Conversation + can invoke skills (including AutomationSkill). */
    ASSISTANT
}

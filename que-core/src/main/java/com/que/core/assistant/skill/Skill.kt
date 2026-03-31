package com.que.core.assistant.skill

import com.que.core.assistant.AssistantEvent

/**
 * A Skill is a capability the assistant can invoke beyond pure conversation.
 *
 * When the IntentClassifier returns AUTOMATE, the SkillRouter looks up
 * the right Skill for the task and calls execute().
 *
 * Skills run asynchronously and communicate progress back via the
 * onEvent callback, which maps to AssistantEvent emissions in QueAssistant.
 */
interface Skill {

    /** Unique name used by SkillRouter for lookup. */
    val name: String

    /**
     * Execute the skill for the given task description.
     *
     * @param task       Natural language task description from IntentClassifier.
     * @param onEvent    Callback to emit events back to QueAssistant.
     *                   Call this for progress, questions, confirmations, finish, fail.
     */
    suspend fun execute(task: String, onEvent: suspend (AssistantEvent) -> Unit)

    /**
     * Pass a user reply (from replyToAgent) into a running skill.
     * Called when the skill emitted AgentQuestion or AgentConfirmation
     * and the user responded.
     */
    fun deliverReply(reply: String)

    /** Stop the skill immediately (user cancelled, or new task came in). */
    fun stop()

    /** True if the skill is currently running. */
    val isRunning: Boolean
}

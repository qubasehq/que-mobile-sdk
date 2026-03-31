package com.que.core.assistant.skill

import com.que.core.assistant.AssistantEvent
import com.que.core.service.Agent
import com.que.core.model.AgentEvent
import com.que.core.model.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * AutomationSkill wraps the existing Agent as a Skill.
 *
 * Translation table:
 *   AgentEvent.Narration(msg, type)         → AssistantEvent.AgentNarration(msg, type)
 *   AgentEvent.UserQuestionAsked(q, opts)   → AssistantEvent.AgentQuestion(q, opts)
 *   AgentEvent.ConfirmationRequired(s, p)   → AssistantEvent.AgentConfirmation(s, p)
 *   AgentState.Finished                     → AssistantEvent.AgentFinished(summary)
 *   AgentState.Error                        → AssistantEvent.AgentFailed(reason)
 *
 * Agent is NOT modified. AutomationSkill is purely a bridge.
 */
class AutomationSkill(
    private val agent: Agent,
    private val scope: CoroutineScope
) : Skill {

    override val name: String = "automation"
    override val isRunning: Boolean get() = _isRunning
    private var _isRunning = false
    private var eventCallback: (suspend (AssistantEvent) -> Unit)? = null

    init {
        // Subscribe to AgentEvent stream and translate to AssistantEvent
        scope.launch {
            agent.events.collect { agentEvent ->
                val assistantEvent = when (agentEvent) {
                    is AgentEvent.Narration ->
                        AssistantEvent.AgentNarration(agentEvent.message, agentEvent.type)
                    is AgentEvent.UserQuestionAsked ->
                        AssistantEvent.AgentQuestion(agentEvent.question, agentEvent.options)
                    is AgentEvent.ConfirmationRequired ->
                        AssistantEvent.AgentConfirmation(agentEvent.summary, agentEvent.actionPreview)
                    is AgentEvent.UserReplied -> null  // Internal — no need to surface
                    is AgentEvent.TaskDecomposed -> null // Internal planning — no need to surface
                }
                assistantEvent?.let { eventCallback?.invoke(it) }
            }
        }
    }

    override suspend fun execute(task: String, onEvent: suspend (AssistantEvent) -> Unit) {
        _isRunning = true
        eventCallback = onEvent

        onEvent(AssistantEvent.AgentNarration(">>>> [AUTOMATION_SKILL] execute() called with task: $task", "log"))
        onEvent(AssistantEvent.AgentStarted(task))
        onEvent(AssistantEvent.AgentNarration(">>>> [AUTOMATION_SKILL] AgentStarted emitted, calling agent.run()...", "log"))

        try {
            val finalState = agent.run(task)
            onEvent(AssistantEvent.AgentNarration(">>>> [AUTOMATION_SKILL] agent.run() returned: $finalState", "log"))

            _isRunning = false

            when (finalState) {
                is AgentState.Finished ->
                    onEvent(AssistantEvent.AgentFinished(finalState.result))
                is AgentState.Error ->
                    onEvent(AssistantEvent.AgentFailed(finalState.message))
                else ->
                    onEvent(AssistantEvent.AgentFailed("Agent stopped unexpectedly"))
            }
        } catch (e: Exception) {
            onEvent(AssistantEvent.AgentNarration(">>>> [AUTOMATION_SKILL] agent.run() THREW: ${e.javaClass.name}: ${e.message}", "log"))
            e.printStackTrace()
            _isRunning = false
            onEvent(AssistantEvent.AgentFailed("Agent crashed: ${e.message}"))
        }
    }

    override fun deliverReply(reply: String) {
        agent.resumeWithUserReply(reply)
    }

    override fun stop() {
        agent.stop()
        _isRunning = false
    }
}

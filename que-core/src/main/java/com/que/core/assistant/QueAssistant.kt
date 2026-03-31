package com.que.core.assistant

import com.que.core.assistant.skill.AutomationSkill
import com.que.core.assistant.skill.SkillRouter
import com.que.core.assistant.skill.Skill
import com.que.core.service.Agent
import com.que.core.service.LLMClient
import com.que.core.service.Message
import com.que.core.service.Role
import com.que.core.service.SpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * QueAssistant — voice-first conversational AI with automation capability.
 *
 * USAGE:
 *
 *   val assistant = QueAssistant(
 *       llm       = myLLMClient,
 *       agent     = myQueAgent,       // Your existing Agent instance
 *       speech    = mySpeechService,
 *       settings  = AssistantSettings(mode = AssistantMode.ASSISTANT)
 *   )
 *
 *   // Collect events in your UI
 *   lifecycleScope.launch {
 *       assistant.events.collect { event ->
 *           when (event) {
 *               is AssistantEvent.SpeakResponse  -> tts.speak(event.text)
 *               is AssistantEvent.AgentStarted   -> showBanner(event.taskDescription)
 *               is AssistantEvent.AgentNarration  -> updateBanner(event.message)
 *               is AssistantEvent.AgentQuestion   -> showQuestion(event.question, event.options)
 *               is AssistantEvent.AgentFinished   -> hideBanner()
 *               ...
 *           }
 *       }
 *   }
 *
 *   // Send a message
 *   assistant.send("open Spotify")
 *   assistant.send("what's 12 times 8?")
 *
 *   // Reply to an agent question or confirmation
 *   assistant.replyToAgent("Yes, send it")
 *
 *   // Stop everything
 *   assistant.stop()
 *   assistant.dispose()
 */
class QueAssistant(
    private val llm: LLMClient,
    private val agent: Agent,
    private val speech: SpeechService? = null,
    private val settings: AssistantSettings = AssistantSettings(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    // ── Public event stream ────────────────────────────────────────────────
    private val _events = MutableSharedFlow<AssistantEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AssistantEvent> = _events.asSharedFlow()

    // ── Internal components ────────────────────────────────────────────────
    private val memory = AssistantMemory(settings.conversationWindowSize)
    private val classifier = IntentClassifier(llm)
    private val conversation = ConversationHandler(llm, memory, settings)
    private val skillRouter = SkillRouter()
    private var activeSkill: Skill? = null

    init {
        // Register the automation skill (backed by the provided QueAgent)
        skillRouter.register(AutomationSkill(agent, scope))
    }

    // ── Main input entry point ─────────────────────────────────────────────

    /**
     * Send a user message (text or transcribed speech) to the assistant.
     * This is the main input method. Non-blocking — launches in the SDK scope.
     */
    fun send(utterance: String) {
        scope.launch { process(utterance) }
    }

    /**
     * Deliver a user reply to a running automation skill.
     * Call this when the UI receives AgentQuestion or AgentConfirmation
     * and the user has responded.
     */
    fun replyToAgent(reply: String) {
        activeSkill?.deliverReply(reply)
    }

    /**
     * Stop any running automation immediately.
     */
    fun stop() {
        skillRouter.stopAll()
        activeSkill = null
    }

    /**
     * Clear conversation memory (start fresh).
     */
    fun clearMemory() {
        memory.clear()
    }

    /**
     * Clean up all resources. Call when the assistant is no longer needed
     * (e.g. onDestroy of your Service or Activity).
     */
    fun dispose() {
        stop()
        scope.cancel()
    }

    // ── Core processing logic ──────────────────────────────────────────────

    private suspend fun process(utterance: String) {
        emit(AssistantEvent.ThinkingStarted)

        try {
            when (settings.mode) {

                AssistantMode.CHAT -> {
                    // CHAT mode: always just talk, never automate
                    val reply = conversation.reply(utterance)
                    emit(AssistantEvent.ThinkingEnded)
                    emit(AssistantEvent.SpeakResponse(reply))
                    if (settings.enableSpeech) speech?.speak(reply)
                }

                AssistantMode.ASSISTANT -> {
                    // ASSISTANT mode: classify first, then route
                    emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Calling IntentClassifier...", "log"))
                    val intent = classifier.classify(utterance, memory.getHistory()) { msg ->
                        emit(AssistantEvent.AgentNarration(msg, "log"))
                    }
                    emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Classifier returned: ${intent.javaClass.simpleName}", "log"))
                    emit(AssistantEvent.ThinkingEnded)

                    when (intent) {
                        is IntentClassifier.Intent.Chat -> {
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Intent classified as CHAT", "log"))
                            val reply = conversation.reply(utterance)
                            emit(AssistantEvent.SpeakResponse(reply))
                            if (settings.enableSpeech) speech?.speak(reply)
                        }

                        is IntentClassifier.Intent.Automate -> {
                            // Acknowledge verbally before starting automation
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Intent classified as AUTOMATE: ${intent.task}", "log"))
                            val ack = generateAck(intent.task)
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] SpeakResponse emitted: $ack", "log"))
                            emit(AssistantEvent.SpeakResponse(ack, speak = true))
                            if (settings.enableSpeech) speech?.speak(ack)
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] TTS speak done", "log"))

                            // Stop any previously running skill
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Stopping previous skill...", "log"))
                            activeSkill?.stop()
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Previous skill stopped", "log"))

                            // Route to the right skill
                            val skill = skillRouter.route(intent.task)
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Skill routed: ${skill?.name ?: "NULL"}", "log"))
                            if (skill == null) {
                                val fallback = "I can't do that right now."
                                emit(AssistantEvent.SpeakResponse(fallback))
                                if (settings.enableSpeech) speech?.speak(fallback)
                                return
                            }

                            activeSkill = skill
                            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] About to call skill.execute()", "log"))

                            // Execute skill — this bridges all AgentEvents back to AssistantEvents
                            try {
                                skill.execute(intent.task) { event ->
                                    if (event !is AssistantEvent.AgentNarration || event.type != "log") {
                                        emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] Skill event: ${event.javaClass.simpleName}", "log"))
                                    }
                                    emit(event)

                                    // Speak narration findings aloud
                                    if (event is AssistantEvent.AgentNarration && event.type == "found") {
                                        if (settings.enableSpeech) speech?.speak(event.message)
                                    }

                                    // When agent finishes, inject result context into conversation memory
                                    if (event is AssistantEvent.AgentFinished) {
                                        conversation.injectContext(event.summary)
                                        if (settings.enableSpeech) speech?.speak(event.summary)
                                    }
                                }
                                emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] skill.execute() returned normally", "log"))
                            } catch (e: Exception) {
                                emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] skill.execute() THREW: ${e.message}", "log"))
                                e.printStackTrace()
                                emit(AssistantEvent.AgentFailed(e.message ?: "Skill execution failed"))
                            }

                            activeSkill = null
                        }
                    }
                }
            }

        } catch (e: Exception) {
            emit(AssistantEvent.AgentNarration(">>>> [QUE_ASSISTANT] process() GLOBAL CATCH: ${e.javaClass.name}: ${e.message}", "log"))
            e.printStackTrace()
            emit(AssistantEvent.ThinkingEnded)
            emit(AssistantEvent.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun generateAck(task: String): String {
        // Short LLM-generated acknowledgement like "On it." / "Sure, doing that."
        // This is a tiny call — 1 shot, no history needed
        val messages = listOf(
            Message(Role.SYSTEM, """
                Generate a very short (2-5 words) natural acknowledgement for this phone task.
                Examples: "On it.", "Sure.", "Got it, one sec.", "Opening that now."
                Respond with ONLY the acknowledgement. Nothing else.
            """.trimIndent()),
            Message(Role.USER, task)
        )
        return try {
            val text = llm.generate(messages).text.trim()
            if (text.isBlank()) "On it." else text
        } catch (e: Exception) {
            "On it."
        }
    }

    private suspend fun emit(event: AssistantEvent) {
        _events.emit(event)
    }
}

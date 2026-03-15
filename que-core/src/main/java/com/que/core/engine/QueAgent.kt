package com.que.core.engine
import com.que.core.interruption.InterruptionAction
import com.que.core.interruption.InterruptionContext
import com.que.core.interruption.InterruptionDetector
import com.que.core.interruption.InterruptionHandler
import com.que.core.interruption.InterruptionType
import com.que.core.model.AgentCheckpoint
import com.que.core.model.AgentEvent
import com.que.core.model.AgentOutput
import com.que.core.model.AgentSettings
import com.que.core.model.AgentState
import com.que.core.model.AgentStepHistory
import com.que.core.model.StepInfo
import com.que.core.registry.ElementRegistry
import com.que.core.service.Action
import com.que.core.service.ActionExecutor
import com.que.core.service.ActionParser
import com.que.core.service.ActionResult
import com.que.core.service.Agent
import com.que.core.service.ContextualMemory
import com.que.core.service.FileSystem
import com.que.core.service.InMemoryMemoryStore
import com.que.core.service.LLMClient
import com.que.core.service.Memory
import com.que.core.service.MemoryContext
import com.que.core.service.Message
import com.que.core.service.PerceptionEngine
import com.que.core.service.ScreenSnapshot
import com.que.core.service.SpeechService
import com.que.core.service.UserGuidance
import com.que.core.strategy.ActionPlan
import com.que.core.strategy.AdaptiveLearningSystem
import com.que.core.strategy.ExecutionContext
import com.que.core.strategy.IntelligentRecoverySystem
import com.que.core.strategy.PredictivePlanner
import com.que.core.strategy.RecoveryStrategy
import com.que.core.strategy.RetryStrategy
import com.que.core.strategy.SmartRetryStrategy
import com.que.core.strategy.StuckStateDetector
import com.que.core.util.AgentLogger

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * COMPLETELY REWRITTEN Agent Loop Logic
 * 
 * This implementation surpasses Blurr by:
 * 1. Proper Memory Management with AgentMemoryManager
 * 2. Multi-action support (execute multiple actions per step)
 * 3. State tracking across steps
 * 4. Structured history with metadata
 * 5. Corrective feedback on errors
 * 6. Better type safety and error handling
 */
/**
 * The core Agent class responsible for perceiving the environment, thinking (reasoning via LLM), and acting.
 * 
 * @property context The Android Application Context.
 * @property perceptionEngine Helper for capturing and analyzing screen content.
 * @property actionExecutor Helper for performing accessibility actions.
 * @property llmClient Client for interacting with the LLM (Gemini).
 */
class QueAgent(
    private val context: android.content.Context,
    private val perception: PerceptionEngine,
    private val executor: ActionExecutor,
    private val llm: LLMClient,
    private val fileSystem: FileSystem,
    private val settings: AgentSettings = AgentSettings(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob()),
    private val speech: SpeechService? = null,
    private val contextualMemory: ContextualMemory = InMemoryMemoryStore(),
    private val guidance: UserGuidance? = null
) : Agent {

    private val TAG = "QueAgent"
    private val interruptionDetector = InterruptionDetector(context)
    private val interruptionHandler = InterruptionHandler()

    fun dispose() {
        scope.cancel()
        try {
            memory.clear()
            history.clear()
            ElementRegistry.clear()
        } catch (e: Exception) {
            AgentLogger.e(TAG, "Error disposing agent: ${e.message}")
        }
    }

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    override val state: StateFlow<AgentState> = _state.asStateFlow()

    // Event flow for bidirectional communication (ask_user, narrate, confirm)
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    // Channel for receiving user replies when the loop is paused for ask_user/confirm
    private val userReplyChannel = Channel<String>(capacity = 1)

    private var currentJob: Job? = null
    private val promptBuilder = PromptBuilder()
    
    // NEW: Proper components for robust agent loop
    private val memory = AgentMemoryManager()
    private val loopState = AgentLoopState()
    private val history = mutableListOf<AgentStepHistory>()
    
    // Pause/Resume functionality
    private var isPaused: Boolean = false
    private var pauseResumeSignal = kotlinx.coroutines.sync.Mutex()
    
    // NEW: Predictive Planner
    private val planner: PredictivePlanner = PredictivePlanner(llm)
    private var currentPlan: ActionPlan? = null
    private var currentPlanStepIndex: Int = 0
    
    // NEW: Intelligent Recovery
    private val recoverySystem: IntelligentRecoverySystem = IntelligentRecoverySystem(contextualMemory)
    private var consecutiveFailures: Int = 0

    // NEW: Adaptive Learning
    private val learningSystem: AdaptiveLearningSystem? = if (settings.enableAdaptiveLearning) {
        AdaptiveLearningSystem(contextualMemory, llm)
    } else {
        null
    }
    
    // NEW: Advanced Features from Phase 5
    private val stuckDetector = StuckStateDetector()
    private val smartRetry = SmartRetryStrategy()
    
    private fun log(message: String, level: String = "D") {
        if (settings.enableLogging) {
            when (level) {
                "E" -> AgentLogger.e(TAG, message)
                "W" -> AgentLogger.w(TAG, message)
                "I" -> AgentLogger.i(TAG, message)
                else -> AgentLogger.d(TAG, message)
            }
        }
    }

    /**
     * Starts the agent's main loop with the given instruction.
     * This method is non-blocking and launches a new coroutine.
     *
     * @param instruction The high-level task for the agent to perform.
     */
    override suspend fun run(instruction: String): AgentState {
        stop()

        currentJob = scope.launch {
            try {
                // ... setup ...
                // Reset state
                loopState.nSteps = 0
                loopState.stopped = false
                loopState.consecutiveFailures = 0
                loopState.lastModelOutput = null
                loopState.lastResults = emptyList()
                history.clear()
                currentPlan = null
                currentPlanStepIndex = 0
                
                // Initialize memory with task
                val systemPrompt = promptBuilder.buildSystemPrompt()
                // Update system prompt with fresh builder (to get new multi-action instructions)
                memory.addTask(instruction, systemPrompt)
                stuckDetector.clear()
                AgentLogger.d(TAG, "Starting step ${loopState.nSteps}")
                
                loop(instruction)
            } catch (e: kotlinx.coroutines.CancellationException) {
                AgentLogger.i(TAG, "Task cancelled")
                _state.value = AgentState.Idle
            } catch (e: Exception) {
                AgentLogger.e(TAG, "Critical error in agent loop", e)
                _state.value = AgentState.Error("Agent crashed", e)
                if (settings.enableSpeech) {
                    speech?.speak("I crashed. Sorry.")
                }
            }
        }
        
        currentJob?.join()
        return _state.value
    }

    override fun stop() {
        currentJob?.cancel()
        loopState.stopped = true
        isPaused = false
        _state.value = AgentState.Idle
    }
    
    override fun pause() {
        isPaused = true
        _state.value = AgentState.Thinking("Paused")
    }
    
    override fun resume() {
        isPaused = false
        // Resume is handled in the main loop
    }
    
    override fun isPaused(): Boolean = isPaused
    
    /**
     * Resume the agent loop after it paused for user input.
     * Called from the platform layer when the user answers a question or confirms/denies.
     */
    override fun resumeWithUserReply(reply: String) {
        log("User replied: $reply", "I")
        scope.launch {
            _events.emit(AgentEvent.UserReplied(reply))
            userReplyChannel.send(reply)
        }
    }
    
    /**
     * Waits while the agent is paused, checking periodically
     */
    private suspend fun waitForResume() {
        while (isPaused && !loopState.stopped) {
            kotlinx.coroutines.delay(100)
        }
    }

    /**
     * THE MAIN AGENT LOOP - Completely rewritten to match and exceed Blurr
     */
    private suspend fun loop(task: String) {
        log("=== AGENT LOOP STARTING ===", "I")
        log("Task: $task", "I")
        log("Max Steps: ${settings.maxSteps}", "I")
        
        while (!loopState.stopped && loopState.nSteps < settings.maxSteps) {
            // Check for pause state
            if (isPaused) {
                _state.value = AgentState.Thinking("Paused - waiting for resume")
                waitForResume()
                if (loopState.stopped) break
                _state.value = AgentState.Thinking("Resumed")
                kotlinx.coroutines.delay(1000) // Give time to settle
            }
            
            loopState.nSteps++
            val stepStartTime = System.currentTimeMillis()
            val stepSpeechLog = mutableListOf<String>()
            
            // Helper to speak and log
            fun speak(text: String) {
                if (settings.enableSpeech) {
                    speech?.speak(text)
                    stepSpeechLog.add(text)
                }
            }
            
            log("\\n=== STEP ${loopState.nSteps}/${settings.maxSteps} ===", "I")
            
            // Show progress
            guidance?.showProgress(loopState.nSteps, settings.maxSteps, "Starting step ${loopState.nSteps}")

            // 1. CHECK FOR INTERRUPTIONS
            log(" Checking for interruptions...", "D")
            val interruption = interruptionDetector.detectInterruption()
            if (interruption != null) {
                log(" INTERRUPTION DETECTED: $interruption", "W")
                val shouldContinue = handleInterruption(interruption)
                if (!shouldContinue) {
                    log(" Interruption requires stopping execution", "W")
                    break
                }
            } else {
                log(" No interruption detected", "D")
            }
            
            // 2. SENSE
            val screen = sense()
            if (screen == null) {
                // If sense returns null (e.g. stuck and recovering), skip this iteration
                continue
            }

            // 2 & 3. THINK
            val agentOutput = think(task, screen)
            if (agentOutput == null) {
                // If think failed (retries exhausted), handle failure state in think() or check here
                // think() handles internal retries and failure logging, so if null, we stop or retry as appropriate
                if (_state.value is AgentState.Error) break
                // If it's just a retry loop inside loop, we continue
                continue
            }

            // 4 & 5. ACT & RECORD
            val shouldStop = act(agentOutput, screen, stepStartTime, stepSpeechLog)
            if (shouldStop) break

            // Small delay between steps
            kotlinx.coroutines.delay(1000)
        }

        // Loop finished
        if (loopState.nSteps >= settings.maxSteps) {
            log("--- 🏁 Agent reached max steps (${settings.maxSteps}). Stopping. ---", "W")
            _state.value = AgentState.Error("Max steps reached. Task may be too complex.")
        } else if (!loopState.stopped) {
            log("--- 🏁 Agent run finished. ---", "I")
        }
        
        log("=== AGENT LOOP COMPLETE ===", "I")
        log("Total steps: ${loopState.nSteps}", "I")
        log("History entries: ${history.size}", "I")
    }

    /**
     * Execute action with retry logic if configured
     */
    private suspend fun executeActionWithRecovery(action: Action, screen: ScreenSnapshot): ActionResult {
        var result = executor.execute(action)
        
        if (!result.success) {
            log("Action failed: ${result.message}", "W")
            
            val context = ExecutionContext(
                lastAction = action,
                appName = screen.activityName, 
                consecutiveFailures = consecutiveFailures + 1
            )
            
            val strategy = recoverySystem.handleError(result, context)
            
            if (strategy != null) {
                log("Applying recovery strategy: ${strategy::class.simpleName}", "I")
                
                when (strategy) {
                    is RecoveryStrategy.Retry -> {
                        // Use SmartRetryStrategy to determine intelligent backoff
                        val smartStrategy = smartRetry.determineStrategy(result)
                        log("Smart Retry Strategy: $smartStrategy", "I")
                        
                        // If no specific smart strategy, fallback to the one from RecoverySystem
                        // But usually SmartRetryStrategy covers it
                        if (smartStrategy is RetryStrategy.NoRetry) {
                             // Fallback manual retry loop from original logic if smart says no, 
                             // but actually we should trust smart strategy.
                             // Let's force a retry if RecoverySystem explicitly asked for it
                             val manualStrategy = RetryStrategy.Immediate(maxAttempts = strategy.maxAttempts)
                             result = smartRetry.executeWithRetry({ executor.execute(action) }, manualStrategy)
                        } else {
                             result = smartRetry.executeWithRetry({ executor.execute(action) }, smartStrategy)
                        }
                    }
                    
                    is RecoveryStrategy.ScrollAndRetry -> {
                        executor.execute(Action.Scroll(strategy.direction))
                        kotlinx.coroutines.delay(1000)
                        result = executor.execute(action)
                    }
                    
                    is RecoveryStrategy.RestartApp -> {
                        executor.execute(Action.Home)
                        kotlinx.coroutines.delay(500)
                        executor.execute(Action.OpenApp(strategy.appName))
                        kotlinx.coroutines.delay(3000)
                        result = executor.execute(action)
                    }
                    
                    else -> {
                        // Abandon or other unsupported strategy
                    }
                }
                
                // Record outcome
                recoverySystem.recordRecovery(result, strategy, result.success, context)
            }
        }
        
        return result
    }

    /**
     * Generate AgentOutput from LLM response
     * Supports multi-action format
     */
    private suspend fun generateAgentOutput(messages: List<Message>): AgentOutput? {
        var attempts = 0
        
        while (attempts < settings.maxRetries) {
            try {
                val response = llm.generate(messages)
                val responseText = response.json ?: response.text
                
                if (responseText.isBlank()) {
                    attempts++
                    continue
                }
                
                return parseAgentOutput(responseText)
            } catch (e: Exception) {
                log("LLM generation attempt ${attempts + 1} failed: ${e.message}", "W")
                attempts++
                if (attempts >= settings.maxRetries) {
                    return null
                }
                kotlinx.coroutines.delay(1000 * attempts.toLong())
            }
        }
        
        return null
    }

    private fun extractJson(text: String): String? {
        var depth = 0
        var startIdx = -1
        
        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) startIdx = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && startIdx != -1) {
                        // Found the first valid top-level object, return it
                        // (Assuming we only care about the first correct JSON block)
                        return text.substring(startIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    /**
     * Parse LLM response into AgentOutput with multi-action support
     */
    private fun parseAgentOutput(jsonStr: String): AgentOutput? {
        try {
            // Remove markdown code blocks
            val cleaned = jsonStr
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            
            log("Raw LLM Response (first 500 chars): ${cleaned.take(500)}", "D")

            // Find JSON boundaries properly using a stack-based approach
            val jsonContent = extractJson(cleaned)
            
            if (jsonContent == null) {
                log("No valid JSON found in response", "E")
                return null
            }
            
            
            log("Cleaned JSON: $jsonContent", "D")
            // Parse with proper error handling
            val kotlinxJson = Json { 
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
            
            val jsonElement = kotlinxJson.parseToJsonElement(jsonContent).jsonObject
            
            // Validate required fields
            if (!jsonElement.containsKey("thought") && !jsonElement.containsKey("actions")) {
                 // Relaxed check, but at least one should be there
            }
            
            val thought = jsonElement["thought"]?.jsonPrimitive?.content ?: ""
            val nextGoal = jsonElement["nextGoal"]?.jsonPrimitive?.content ?: thought
            val confidence = jsonElement["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
            
            // Parse actions - support both "action" (singular) and "actions" (plural array)
            val actionsJson = when {
                // Check for "actions" array first (preferred format)
                jsonElement.containsKey("actions") -> {
                    jsonElement["actions"]?.jsonArray ?: JsonArray(emptyList())
                }
                // Fallback to "action" singular object (for backward compatibility)
                jsonElement.containsKey("action") -> {
                    val singleAction = jsonElement["action"]?.jsonObject
                    if (singleAction != null) {
                        JsonArray(listOf(singleAction))
                    } else {
                        JsonArray(emptyList())
                    }
                }
                else -> JsonArray(emptyList())
            }
            
            log("Actions JSON array size: ${actionsJson.size}", "D")
            
            val actions = actionsJson.mapNotNull { actionElement ->
                val parsed = ActionParser.parse(actionElement.jsonObject)
                if (parsed == null) {
                    log("Failed to parse action: $actionElement", "W")
                }
                parsed
            }
            
            if (actions.isEmpty() && thought.isBlank()) {
                log("No valid actions or thought found", "E")
                return null
            }
            
            return AgentOutput(thought, nextGoal, actions, confidence)
        } catch (e: Exception) {
            log("Failed to parse AgentOutput: ${e.message}", "E")
            return null
        }
    }

    /**
     * Parse a single action from JSON
     */


    /**
     * Get the complete history (for debugging/analysis)
     */
    fun getHistory(): List<AgentStepHistory> = history.toList()
    
    /**
     * Get current loop state (for monitoring)
     */
    /**
     * PHASE 1: SENSE
     * Captures screen state and checks for stuck conditions.
     * Returns null if the agent should skip the rest of the loop (e.g. recovering from stuck state).
     */
    private suspend fun sense(): ScreenSnapshot? {
        log("👀 Sensing screen state...", "I")
        _state.value = AgentState.Perceiving
        val screen = perception.capture()

        // Check for stuck state
        stuckDetector.recordState(screen)
        if (stuckDetector.isStuck()) {
            log("⚠️ Agent appears stuck in the same state for too long!", "W")
            val recovery = stuckDetector.suggestRecovery(screen)
            if (recovery != null && recovery is RecoveryStrategy.AlternativeAction) {
                log("♻️ Attempting to unstick via Back action", "I")
                executor.execute(recovery.actions.first())
                kotlinx.coroutines.delay(1000)
                // Return null to signal the loop to continue/retry
                return null
            }
        }
        return screen
    }

    /**
     * PHASE 2: THINK
     * Prepares memory, handles predictive planning, and queries the LLM.
     */
    private suspend fun think(task: String, screen: ScreenSnapshot): AgentOutput? {
        // Recall memories
        val memoryContext = MemoryContext(app = screen.activityName)
        val relevantMemories = contextualMemory.recall(task, memoryContext)

        // Prepare Prompt
        log("🧠 Preparing prompt with memory...", "I")
        _state.value = AgentState.Thinking()
        memory.addStateMessage(
            modelOutput = loopState.lastModelOutput,
            results = loopState.lastResults,
            stepInfo = StepInfo(loopState.nSteps, settings.maxSteps),
            screen = screen,
            history = history,
            relevantMemories = relevantMemories
        )

        log("🤔 Asking LLM for next action(s)...", "I")

        // Predictive Planning Integration
        val agentOutput = if (settings.enablePredictivePlanning && loopState.planningFailures < 2) {
            executePredictivePlanning(task, screen)
        } else {
            generateStandardOutput(task, screen)
        }

        // Handle LLM Failure
        if (agentOutput == null) {
            handleThinkFailure(screen)
            return null
        }

        // Success - clean up failure state
        loopState.consecutiveFailures = 0
        loopState.lastModelOutput = agentOutput
        log("🤖 LLM decided: ${agentOutput.nextGoal}", "I")
        log("   Thought: ${agentOutput.thought}", "I")
        log("   Actions: ${agentOutput.actions.size}", "I")
        
        guidance?.showDecision(agentOutput.thought, agentOutput.confidence)
        
        return agentOutput
    }

    private suspend fun executePredictivePlanning(task: String, screen: ScreenSnapshot): AgentOutput? {
        if (currentPlan == null) {
            log("🔮 Generating predictive plan...", "I")
            currentPlan = planner.planAhead(task, screen, history)
            if (currentPlan!!.steps.isNotEmpty()) {
                log("📋 Plan generated: ${currentPlan!!.steps.size} steps", "I")
                loopState.planningFailures = 0
            } else {
                log("⚠️ Plan generation failed (empty). Fallback to standard thinking.", "W")
                currentPlan = null
                loopState.planningFailures++
                if (loopState.planningFailures >= 2) {
                    log("🚫 Disabling predictive planning due to repeated failures.", "W")
                }
            }
        }

        if (currentPlan != null && currentPlanStepIndex < currentPlan!!.steps.size) {
            val step = currentPlan!!.steps[currentPlanStepIndex]
            log("👉 Executing planned step ${currentPlanStepIndex + 1}/${currentPlan!!.steps.size}: ${step.description}", "I")
            return AgentOutput(
                thought = "Following plan: ${step.description}",
                nextGoal = step.description,
                actions = listOf(step.action),
                confidence = currentPlan!!.confidence
            )
        } else {
            currentPlan = null
            return generateStandardOutput(task, screen)
        }
    }

    private suspend fun generateStandardOutput(task: String, screen: ScreenSnapshot): AgentOutput? {
        val messages = memory.getMessages().toMutableList()
        if (learningSystem != null) {
            messages.addAll(learningSystem.generateImprovedPrompt(task, screen, history))
        }
        return generateAgentOutput(messages)
    }

    private suspend fun handleThinkFailure(screen: ScreenSnapshot) {
        log("❌ LLM failed to return valid output", "E")
        loopState.consecutiveFailures++
        memory.addCorrectionNote(
            "Your previous output was not valid JSON. Please ensure your response is correctly formatted with 'thought', 'nextGoal', and 'actions' array."
        )

        if (loopState.consecutiveFailures >= settings.maxRetries) {
            log("❌ Agent failed too many times consecutively. Stopping.", "E")
            _state.value = AgentState.Error("Agent failed after ${settings.maxRetries} consecutive failures")
            if (settings.enableSpeech) speech?.speak("I failed to complete the task after multiple attempts.")
            
            history.add(
                AgentStepHistory(
                    step = loopState.nSteps,
                    modelOutput = null,
                    results = emptyList(),
                    screenState = screen,
                    timestamp = System.currentTimeMillis(), // Approximate
                    durationMs = 0,
                    failureCount = loopState.consecutiveFailures,
                    speechLog = emptyList(), // Lost context here, but minor
                    systemNotes = listOf("Max failures reached")
                )
            )
        } else {
            if (settings.enableSpeech) speech?.speak("I'm having trouble understanding. Retrying...")
            kotlinx.coroutines.delay(1000)
        }
    }

    /**
     * PHASE 3: ACT (and RECORD)
     * Executes actions, records history, learning, and updates plan.
     * Returns true if the loop should stop (task finished).
     */
    private suspend fun act(
        agentOutput: AgentOutput, 
        screen: ScreenSnapshot, 
        stepStartTime: Long, 
        speechLog: List<String>
    ): Boolean {
        log("💪 Executing ${agentOutput.actions.size} action(s)...", "I")
        val actionResults = mutableListOf<ActionResult>()

        for ((index, action) in agentOutput.actions.withIndex()) {
            // Handle bidirectional communication actions BEFORE executing
            when (action) {
                is Action.AskUser -> {
                    log("🙋 ask_user: ${action.question}", "I")
                    _events.emit(AgentEvent.UserQuestionAsked(action.question, action.options.ifEmpty { null }))
                    _state.value = AgentState.WaitingForUser(
                        reason = "question",
                        question = action.question,
                        options = action.options.ifEmpty { null }
                    )
                    
                    // Speak the question aloud
                    if (settings.enableSpeech) speech?.speak(action.question)
                    
                    // Pause and wait for user reply (indefinitely)
                    log("⏸️ Agent paused — waiting for user reply...", "I")
                    val reply = userReplyChannel.receive()
                    log("▶️ User replied: $reply", "I")
                    
                    // Inject reply into agent memory
                    memory.addCorrectionNote("User answered your question '${action.question}' with: $reply")
                    
                    actionResults.add(ActionResult(success = true, message = "User replied: $reply", data = mapOf("reply" to reply)))
                    _state.value = AgentState.Thinking("Processing user reply")
                    continue // Skip normal execution, go to next action
                }
                
                is Action.Narrate -> {
                    log("📢 narrate [${action.type}]: ${action.message}", "I")
                    _events.emit(AgentEvent.Narration(action.message, action.type))
                    
                    // Speak key findings aloud
                    if (settings.enableSpeech && action.type == "found") {
                        speech?.speak(action.message)
                    }
                    
                    actionResults.add(ActionResult(success = true, message = "Narrated: ${action.message}"))
                    continue // Non-blocking, just emit and continue
                }
                
                is Action.Confirm -> {
                    log("⚠️ confirm: ${action.summary} — ${action.actionPreview}", "I")
                    _events.emit(AgentEvent.ConfirmationRequired(action.summary, action.actionPreview))
                    _state.value = AgentState.WaitingForUser(
                        reason = "confirmation",
                        question = "${action.summary}\n${action.actionPreview}"
                    )
                    
                    // Speak the confirmation request
                    if (settings.enableSpeech) speech?.speak("${action.summary}. ${action.actionPreview}")
                    
                    // Pause and wait for user confirmation (indefinitely)
                    log("⏸️ Agent paused — waiting for user confirmation...", "I")
                    val reply = userReplyChannel.receive()
                    log("▶️ User confirmation reply: $reply", "I")
                    
                    val approved = reply.lowercase().let { 
                        it == "yes" || it == "confirm" || it == "ok" || it == "approve" || it == "go ahead" || it == "proceed"
                    }
                    
                    if (!approved) {
                        log("🚫 User denied action. Aborting this action chain.", "W")
                        memory.addCorrectionNote("User DENIED your proposed action: '${action.summary}'. They said: '$reply'. Respect their decision and adjust your approach.")
                        actionResults.add(ActionResult(success = false, message = "User denied: $reply"))
                        _state.value = AgentState.Thinking("User denied action, re-planning")
                        break // Stop executing remaining actions in this step
                    }
                    
                    memory.addCorrectionNote("User APPROVED your proposed action: '${action.summary}'.")
                    actionResults.add(ActionResult(success = true, message = "User approved"))
                    _state.value = AgentState.Thinking("User approved, continuing")
                    continue
                }
                
                else -> { /* Normal action — fall through to standard execution below */ }
            }

            _state.value = AgentState.Acting("${action.toHumanReadableString()} (${index + 1}/${agentOutput.actions.size})")
            log("Exec: $action", "D")
            
            // Execute with Recovery
            val result = executeActionWithRecovery(action, screen)
            actionResults.add(result)

            if (result.success) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
            }

            log("  - Action '${action.javaClass.simpleName}': ${if (result.success) "✓" else "✗"} ${result.message}", 
                if (result.success) "I" else "W")

            // Stop chain if one fails
            if (!result.success) {
                log("  - 🛑 Action failed. Stopping current step's execution.", "W")
                break
            }
        }

        loopState.lastResults = actionResults

        // RECORD History
        val stepDuration = System.currentTimeMillis() - stepStartTime
        history.add(
            AgentStepHistory(
                step = loopState.nSteps,
                modelOutput = agentOutput,
                results = actionResults,
                screenState = screen,
                timestamp = stepStartTime,
                durationMs = stepDuration,
                failureCount = loopState.consecutiveFailures,
                speechLog = speechLog
            )
        )

        // Learn from Success
        if (actionResults.all { it.success }) {
            scope.launch {
                val key = "success_${screen.activityName}_${loopState.nSteps}"
                val value = "In ${screen.activityName}, I executed actions: ${agentOutput.actions.map { it.javaClass.simpleName }} to achieve '${agentOutput.nextGoal}'"
                contextualMemory.remember(key, value, MemoryContext(app = screen.activityName))
            }
        }

        // Update Plan
        if (actionResults.all { it.success } && currentPlan != null) {
            currentPlanStepIndex++
            if (currentPlanStepIndex >= currentPlan!!.steps.size) {
                log("🏁 Predictive plan completed.", "I")
                currentPlan = null
            }
        } else if (currentPlan != null) {
            log("🛑 Plan step failed. Invalidating plan.", "W")
            currentPlan = null
        }

        log("⏱️ Step completed in ${stepDuration}ms", "I")

        // Check for Completion
        if (actionResults.any { it.isDone }) {
            log("✅ Agent finished the task!", "I")
            _state.value = AgentState.Finished("Task completed successfully")
            loopState.stopped = true
            if (settings.enableSpeech) speech?.speak("Task finished.")
            return true
        }
        
        return false
    }

    fun getLoopState(): AgentLoopState = loopState.copy()
    
    override suspend fun createCheckpoint(): AgentCheckpoint {
        return AgentCheckpoint(
            taskId = "current_task", // This would need to be passed in or tracked
            step = loopState.nSteps,
            loopState = loopState.copy(),
            history = history.toList(),
            memoryMessages = memory.getMessages()
        )
    }
    
    override suspend fun restoreFromCheckpoint(checkpoint: AgentCheckpoint) {
        // Restore loop state
        loopState.nSteps = checkpoint.step
        loopState.consecutiveFailures = checkpoint.loopState.consecutiveFailures
        loopState.lastModelOutput = checkpoint.loopState.lastModelOutput
        loopState.lastResults = checkpoint.loopState.lastResults
        loopState.planningFailures = checkpoint.loopState.planningFailures
        
        // Restore history
        history.clear()
        history.addAll(checkpoint.history)
        
        // Restore memory messages
        memory.restoreMessages(checkpoint.memoryMessages)
    }
    
    private suspend fun handleInterruption(interruptionType: InterruptionType): Boolean {
        val context = InterruptionContext(
            systemEvent = "Detected interruption: $interruptionType",
            permission = if (interruptionType == InterruptionType.PERMISSION_REVOKED) "accessibility" else null
        )
        
        val response = interruptionHandler.handleInterruption(interruptionType, this, context)
        
        // Handle the response based on the action
        when (response.action) {
            InterruptionAction.WAIT_FOR_RESUME -> {
                // Agent is already paused, wait for resume
                waitForResume()
            }
            InterruptionAction.WAIT_FOR_UNLOCK -> {
                // Wait for device to be unlocked
                while (interruptionDetector.isDeviceLocked()) {
                    kotlinx.coroutines.delay(1000)
                }
                resume()
            }
            InterruptionAction.REQUEST_PERMISSIONS -> {
                // Stop execution and request permissions
                stop()
                _state.value = AgentState.Error("Permissions revoked", RuntimeException("Required permissions not granted"))
                return false // Don't continue
            }
            InterruptionAction.STOP_EXECUTION -> {
                // Stop execution
                stop()
                return false // Don't continue
            }
            InterruptionAction.CONTINUE_EXECUTION -> {
                // Continue execution
                // Nothing to do here
            }
        }
        
        // Return whether we should continue based on the response
        return response.shouldContinue
    }
}

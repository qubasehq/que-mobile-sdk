package com.que.core

import android.util.Log
import com.que.core.AgentLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.que.core.RecoveryStrategy
import com.que.core.InterruptionType
import com.que.core.InterruptionDetector

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
            val interruption = interruptionDetector.detectInterruption()
            if (interruption != null) {
                handleInterruption(interruption)
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
    private suspend fun executeActionWithRecovery(action: Action): ActionResult {
        var result = executor.execute(action)
        
        if (!result.success) {
            log("Action failed: ${result.message}", "W")
            
            val context = ExecutionContext(
                lastAction = action,
                appName = perception.capture().activityName, // Re-capture for context? or use cached
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
            _state.value = AgentState.Acting("${action.javaClass.simpleName} (${index + 1}/${agentOutput.actions.size})")
            log("Exec: $action", "D")
            
            // Execute with Recovery
            val result = executeActionWithRecovery(action)
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
    
    private suspend fun handleInterruption(interruptionType: InterruptionType) {
        val context = ExecutionContext(
            lastAction = loopState.lastModelOutput?.actions?.firstOrNull(),
            appName = perception.capture().activityName,
            consecutiveFailures = consecutiveFailures
        )
        
        val checkpoint = createCheckpoint()
        val strategy = recoverySystem.handleInterruption(interruptionType, context, checkpoint)
        
        if (strategy != null) {
            when (strategy) {
                is RecoveryStrategy.RestoreFromCheckpoint -> {
                    restoreFromCheckpoint(strategy.checkpoint)
                }
                is RecoveryStrategy.Retry -> {
                    // For interruptions, we typically just wait and retry
                    kotlinx.coroutines.delay(strategy.delay)
                }
                is RecoveryStrategy.Abandon -> {
                    // Stop the agent
                    stop()
                }
                else -> {
                    // Other strategies don't apply to interruptions
                }
            }
        }
    }
}

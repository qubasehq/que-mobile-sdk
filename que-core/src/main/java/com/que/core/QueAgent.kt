package com.que.core

import android.util.Log
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

    fun dispose() {
        scope.cancel()
        try {
            memory.clear() // Assuming memory has clear()
            history.clear()
            // ElementRegistry.clear() - ElementRegistry is a singleton, might need care
        } catch (e: Exception) {
            log("Error disposing agent: ${e.message}", "E")
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
    
    private fun log(message: String, level: String = "D") {
        if (settings.enableLogging) {
            when (level) {
                "E" -> Log.e("QueAgent", message)
                "W" -> Log.w("QueAgent", message)
                "I" -> Log.i("QueAgent", message)
                else -> Log.d("QueAgent", message)
            }
        }
    }

    /**
     * Starts the agent's main loop with the given instruction.
     * This method is non-blocking and launches a new coroutine.
     *
     * @param instruction The high-level task for the agent to perform.
     */
    override suspend fun run(instruction: String) {
        stop()
        
        currentJob = scope.launch {
            try {
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
                memory.addTask(instruction, systemPrompt)
                
                loop(instruction)
            } catch (e: kotlinx.coroutines.CancellationException) {
                log("Task cancelled", "I")
                _state.value = AgentState.Idle
            } catch (e: Exception) {
                log("Agent crashed: ${e.message}", "E")
                _state.value = AgentState.Error("Agent crashed", e)
                if (settings.enableSpeech) {
                    speech?.speak("I crashed. Sorry.")
                }
            }
        }
        
        currentJob?.join()
    }

    override fun stop() {
        currentJob?.cancel()
        loopState.stopped = true
        _state.value = AgentState.Idle
    }

    /**
     * THE MAIN AGENT LOOP - Completely rewritten to match and exceed Blurr
     */
    private suspend fun loop(task: String) {
        log("=== AGENT LOOP STARTING ===", "I")
        log("Task: $task", "I")
        log("Max Steps: ${settings.maxSteps}", "I")
        
        while (!loopState.stopped && loopState.nSteps < settings.maxSteps) {
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

            // 1. SENSE: Observe the current state of the screen
            log("👀 Sensing screen state...", "I")
            _state.value = AgentState.Perceiving
            val screen = perception.capture()

            // NEW: Recall memories
            val memoryContext = MemoryContext(app = screen.activityName)
            val relevantMemories = contextualMemory.recall(task, memoryContext)

            // 2. THINK (Prepare Prompt): Update memory with results of LAST step
            // and create new prompt using CURRENT screen state
            log("🧠 Preparing prompt with memory...", "I")
            _state.value = AgentState.Thinking()
            memory.addStateMessage(
                modelOutput = loopState.lastModelOutput,
                results = loopState.lastResults,
                stepInfo = StepInfo(loopState.nSteps, settings.maxSteps),
                screen = screen,
                history = history, // Pass history for context
                relevantMemories = relevantMemories
            )

            // 3. THINK (Get Decision): Send prepared messages to LLM
            log("🤔 Asking LLM for next action(s)...", "I")
            
            // PREDICTIVE PLANNING INTEGRATION
            val agentOutput = if (settings.enablePredictivePlanning) {
                if (currentPlan == null) {
                    log("🔮 Generating predictive plan...", "I")
                    currentPlan = planner.planAhead(task, screen)
                    if (currentPlan!!.steps.isNotEmpty()) {
                        log("📋 Plan generated: ${currentPlan!!.steps.size} steps", "I")
                    } else {
                        log("⚠️ Plan generation returned no steps. Fallback to standard thinking.", "W")
                        currentPlan = null // Fallback
                    }
                }
                
                if (currentPlan != null && currentPlanStepIndex < currentPlan!!.steps.size) {
                    val step = currentPlan!!.steps[currentPlanStepIndex]
                    log("👉 Executing planned step ${currentPlanStepIndex + 1}/${currentPlan!!.steps.size}: ${step.description}", "I")
                    
                    AgentOutput(
                        thought = "Following plan: ${step.description}",
                        nextGoal = step.description,
                        actions = listOf(step.action),
                        confidence = currentPlan!!.confidence
                    )
                } else {
                    currentPlan = null // Plan finished or invalid, fallback
                    val messages = memory.getMessages().toMutableList()
                    if (learningSystem != null) {
                        messages.addAll(learningSystem.generateImprovedPrompt(screen))
                    }
                    generateAgentOutput(messages)
                }
            } else {
                val messages = memory.getMessages().toMutableList()
                if (learningSystem != null) {
                    messages.addAll(learningSystem.generateImprovedPrompt(screen))
                }
                generateAgentOutput(messages)
            }

            // Handle LLM Failure with corrective feedback
            if (agentOutput == null) {
                log("❌ LLM failed to return valid output", "E")
                loopState.consecutiveFailures++
                memory.addCorrectionNote(
                    "Your previous output was not valid JSON. Please ensure your response is correctly formatted with 'thought', 'nextGoal', and 'actions' array."
                )
                
                if (loopState.consecutiveFailures >= settings.maxRetries) {
                    log("❌ Agent failed too many times consecutively. Stopping.", "E")
                    _state.value = AgentState.Error("Agent failed after ${settings.maxRetries} consecutive failures")
                    speak("I failed to complete the task after multiple attempts.")
                    
                    // Record failed step
                    history.add(
                        AgentStepHistory(
                            step = loopState.nSteps,
                            modelOutput = null,
                            results = emptyList(),
                            screenState = screen,
                            timestamp = stepStartTime,
                            durationMs = System.currentTimeMillis() - stepStartTime,
                            failureCount = loopState.consecutiveFailures,
                            speechLog = stepSpeechLog,
                            systemNotes = listOf("Max failures reached")
                        )
                    )
                    break
                }
                
                speak("I'm having trouble understanding. Retrying...")
                kotlinx.coroutines.delay(1000)
                continue
            }
            
            // Reset failure counter on success
            loopState.consecutiveFailures = 0
            loopState.lastModelOutput = agentOutput
            log("🤖 LLM decided: ${agentOutput.nextGoal}", "I")
            log("   Thought: ${agentOutput.thought}", "I")
            log("   Actions: ${agentOutput.actions.size}", "I")
            
            guidance?.showDecision(agentOutput.thought, agentOutput.confidence)
            if (agentOutput.confidence < 0.6f) {
                // Low confidence handling could go here
                // e.g. guidance?.askForHelp(...)
            }

            // 4. ACT: Execute ALL actions from the LLM
            log("💪 Executing ${agentOutput.actions.size} action(s)...", "I")
            val actionResults = mutableListOf<ActionResult>()
            
            for ((index, action) in agentOutput.actions.withIndex()) {
                _state.value = AgentState.Acting("${action.javaClass.simpleName} (${index + 1}/${agentOutput.actions.size})")
                    log("Exec: $action", "D")
                    // Use Intelligent Recovery
                    val result = executeActionWithRecovery(action)
                    actionResults.add(result)
                    
                    if (result.success) {
                        consecutiveFailures = 0 // Reset on success
                    } else {
                        consecutiveFailures++
                    }
                
                log("  - Action '${action.javaClass.simpleName}': ${if (result.success) "✓" else "✗"} ${result.message}", 
                    if (result.success) "I" else "W")
                
                // Stop executing further actions if one fails
                if (!result.success) {
                    log("  - 🛑 Action failed. Stopping current step's execution.", "W")
                    break
                }
            }
            
            loopState.lastResults = actionResults

            // 5. RECORD: Save complete step to structured history
            val stepDuration = System.currentTimeMillis() - stepStartTime
            history.add(
                AgentStepHistory(
                    step = loopState.nSteps,
                    modelOutput = agentOutput,
                    results = actionResults,
                    screenState = screen,
                    timestamp = stepStartTime,
                    durationMs = stepDuration,
                    failureCount = loopState.consecutiveFailures, // Should be 0 here if successful
                    speechLog = stepSpeechLog
                )
            )
            
            // Learn from success
            if (actionResults.all { it.success }) {
                scope.launch {
                    val key = "success_${screen.activityName}_${loopState.nSteps}"
                    val value = "In ${screen.activityName}, I executed actions: ${agentOutput.actions.map { it.javaClass.simpleName }} to achieve '${agentOutput.nextGoal}'"
                    contextualMemory.remember(key, value, MemoryContext(app = screen.activityName))
                }
            }
            
            // Advance plan if successful
            if (actionResults.all { it.success } && currentPlan != null) {
                currentPlanStepIndex++
                if (currentPlanStepIndex >= currentPlan!!.steps.size) {
                    log("🏁 Predictive plan completed.", "I")
                    currentPlan = null
                }
            } else if (currentPlan != null) {
                log("🛑 Plan step failed. Invalidating plan.", "W")
                currentPlan = null // Stop following plan on failure
            }
            
            log("⏱️ Step completed in ${stepDuration}ms", "I")

            // Check for task completion
            if (actionResults.any { it.isDone }) {
                log("✅ Agent finished the task!", "I")
                _state.value = AgentState.Finished("Task completed successfully")
                loopState.stopped = true
                speak("Task finished.")
            }

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
                        for (attempt in 1..strategy.maxAttempts) {
                            log("Retry attempt $attempt/${strategy.maxAttempts}", "I")
                            kotlinx.coroutines.delay(strategy.delay)
                            result = executor.execute(action)
                            if (result.success) break
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
    fun getLoopState(): AgentLoopState = loopState.copy()
}

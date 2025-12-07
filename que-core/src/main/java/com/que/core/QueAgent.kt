package com.que.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
class QueAgent(
    private val perception: PerceptionEngine,
    private val executor: ActionExecutor,
    private val llm: LLMClient,
    private val fileSystem: FileSystem,
    private val settings: AgentSettings = AgentSettings(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val speech: SpeechService? = null
) : Agent {

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    override val state: StateFlow<AgentState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private val promptBuilder = PromptBuilder()
    
    // NEW: Proper components for robust agent loop
    private val memory = AgentMemoryManager()
    private val loopState = AgentLoopState()
    private val history = mutableListOf<AgentStepHistory>()
    
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

    override suspend fun run(instruction: String) {
        stop()
        
        // CRITICAL FIX: Run directly in the suspending function, do NOT launch a new job.
        // This ensures the caller waits for the task to complete.
        try {
            // Reset state
            loopState.nSteps = 0
            loopState.stopped = false
            loopState.consecutiveFailures = 0
            loopState.lastModelOutput = null
            loopState.lastResults = emptyList()
            history.clear()
            
            // Initialize memory with task
            val systemPrompt = promptBuilder.buildSystemPrompt()
            memory.addTask(instruction, systemPrompt)
            
            loop(instruction)
        } catch (e: Exception) {
            log("Agent crashed: ${e.message}", "E")
            _state.value = AgentState.Error("Agent crashed", e)
            if (settings.enableSpeech) {
                speech?.speak("I crashed. Sorry.")
            }
        }
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

            // 1. SENSE: Observe the current state of the screen
            log("👀 Sensing screen state...", "I")
            _state.value = AgentState.Perceiving
            val screen = perception.capture()

            // 2. THINK (Prepare Prompt): Update memory with results of LAST step
            // and create new prompt using CURRENT screen state
            log("🧠 Preparing prompt with memory...", "I")
            _state.value = AgentState.Thinking()
            memory.addStateMessage(
                modelOutput = loopState.lastModelOutput,
                results = loopState.lastResults,
                stepInfo = StepInfo(loopState.nSteps, settings.maxSteps),
                screen = screen
            )

            // 3. THINK (Get Decision): Send prepared messages to LLM
            log("🤔 Asking LLM for next action(s)...", "I")
            val messages = memory.getMessages()
            val agentOutput = generateAgentOutput(messages)

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

            // 4. ACT: Execute ALL actions from the LLM
            log("💪 Executing ${agentOutput.actions.size} action(s)...", "I")
            val actionResults = mutableListOf<ActionResult>()
            
            for ((index, action) in agentOutput.actions.withIndex()) {
                _state.value = AgentState.Acting("${action.javaClass.simpleName} (${index + 1}/${agentOutput.actions.size})")
                
                val result = executeActionWithRetry(action)
                actionResults.add(result)
                
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
    private suspend fun executeActionWithRetry(action: Action): ActionResult {
        var result = executor.execute(action)
        var attempts = 0
        
        while (!result.success && settings.retryFailedActions && attempts < settings.maxRetries) {
            attempts++
            log("Action retry attempt $attempts/${settings.maxRetries}", "W")
            kotlinx.coroutines.delay(500 * attempts.toLong())
            result = executor.execute(action)
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

    /**
     * Parse LLM response into AgentOutput with multi-action support
     */
    private fun parseAgentOutput(jsonStr: String): AgentOutput? {
        try {
            log("Raw LLM Response (first 500 chars): ${jsonStr.take(500)}", "D")
            
            val startIndex = jsonStr.indexOf('{')
            val endIndex = jsonStr.lastIndexOf('}')
            
            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                log("No valid JSON found in response", "E")
                log("Full response: $jsonStr", "E")
                return null
            }

            val cleanJson = jsonStr.substring(startIndex, endIndex + 1)
            log("Cleaned JSON: $cleanJson", "D")
            
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val jsonElement = json.parseToJsonElement(cleanJson).jsonObject
            
            val thought = jsonElement["thought"]?.jsonPrimitive?.content ?: ""
            val nextGoal = jsonElement["nextGoal"]?.jsonPrimitive?.content ?: thought
            
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
            log("Actions JSON: $actionsJson", "D")
            
            val actions = actionsJson.mapNotNull { actionElement ->
                val parsed = parseAction(actionElement.jsonObject)
                if (parsed == null) {
                    log("Failed to parse action: $actionElement", "W")
                }
                parsed
            }
            
            log("Parsed ${actions.size} valid actions out of ${actionsJson.size}", "D")
            
            if (actions.isEmpty()) {
                log("No valid actions found in response", "E")
                log("Thought: $thought", "E")
                log("NextGoal: $nextGoal", "E")
                log("Actions JSON was: $actionsJson", "E")
                return null
            }
            
            return AgentOutput(thought, nextGoal, actions)
        } catch (e: Exception) {
            log("Failed to parse AgentOutput: ${e.message}", "E")
            return null
        }
    }

    /**
     * Parse a single action from JSON
     */
    private fun parseAction(actionObj: JsonObject): Action? {
        try {
            val type = actionObj["type"]?.jsonPrimitive?.content ?: return null
            
            return when (type) {
                "tap" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.Tap(id)
                }
                "type" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.Type(text)
                }
                "scroll" -> {
                    val dir = actionObj["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
                    val direction = if (dir == "up") Direction.UP else Direction.DOWN
                    Action.Scroll(direction)
                }
                "long_press" -> {
                    val id = actionObj["elementId"]?.jsonPrimitive?.intOrNull ?: return null
                    Action.LongPress(id)
                }
                "back" -> Action.Back
                "home" -> Action.Home
                "switch_app" -> Action.SwitchApp
                "open_app" -> {
                    val appName = actionObj["appName"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.OpenApp(appName)
                }
                "search_google" -> {
                    val query = actionObj["query"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.SearchGoogle(query)
                }
                "launch_intent" -> {
                    val intentName = actionObj["intentName"]?.jsonPrimitive?.contentOrNull ?: return null
                    val params = actionObj["parameters"]?.jsonObject?.entries?.associate {
                        it.key to (it.value.jsonPrimitive.contentOrNull ?: "")
                    } ?: emptyMap()
                    Action.LaunchIntent(intentName, params)
                }
                "speak" -> {
                    val text = actionObj["text"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.Speak(text)
                }
                "write_file" -> {
                    val fileName = actionObj["fileName"]?.jsonPrimitive?.contentOrNull ?: return null
                    val content = actionObj["content"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.WriteFile(fileName, content)
                }
                "read_file" -> {
                    val fileName = actionObj["fileName"]?.jsonPrimitive?.contentOrNull ?: return null
                    Action.ReadFile(fileName)
                }
                "finish" -> {
                    // Special action to mark completion
                    // Return a custom action that sets isDone
                    Action.Custom("finish", emptyMap())
                }
                else -> Action.Custom(type, emptyMap())
            }
        } catch (e: Exception) {
            log("Failed to parse action: ${e.message}", "E")
            return null
        }
    }

    /**
     * Get the complete history (for debugging/analysis)
     */
    fun getHistory(): List<AgentStepHistory> = history.toList()
    
    /**
     * Get current loop state (for monitoring)
     */
    fun getLoopState(): AgentLoopState = loopState.copy()
}

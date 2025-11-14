/**
 * Agent - Main orchestrator for the SENSE → THINK → ACT loop
 * 
 * The Agent continuously observes screen state (SENSE), makes decisions via LLM (THINK),
 * and executes actions (ACT) until task completion or termination conditions are met.
 * 
 * Responsibilities:
 * - Initialize all subsystems (Memory, Perception, LLM, Executor, FileSystem, Voice)
 * - Execute SENSE → THINK → ACT loop
 * - Track state (steps, failures, stopped flag)
 * - Handle loop termination conditions
 * - Emit events for monitoring
 * - Implement error recovery strategies
 */

import {
  AgentConfig,
  AgentState,
  AgentResult,
  AgentStep,
  AgentHistory,
  AgentSettings,
  ScreenState,
  AgentOutput,
  ActionResult,
} from '../types'
import { MemoryManager } from '../memory/MemoryManager'
import { FileSystem } from '../memory/FileSystem'
import { Perception } from '../perception/Perception'
import { SemanticParser } from '../perception/SemanticParser'
import { GeminiClient } from '../llm/GeminiClient'
import { ActionExecutor } from '../actions/ActionExecutor'
import { VoiceManager } from '../voice/VoiceManager'
import { AccessibilityModule } from '../native/AccessibilityModule'
import { QueError, ErrorCategory } from '../utils/errors'
import { ClarificationAgent } from '../clarification/ClarificationAgent'
import { DialogueManager } from '../clarification/DialogueManager'

// ============================================================================
// Default Settings
// ============================================================================

const DEFAULT_SETTINGS: AgentSettings = {
  maxHistoryItems: 20,
  stepDelay: 1000,
  maxLLMRetries: 3,
  actionTimeout: 30000,
}

// ============================================================================
// Agent Class
// ============================================================================

export class Agent {
  // Configuration
  private config: AgentConfig
  private settings: AgentSettings

  // Subsystems
  private nativeModule: AccessibilityModule
  private fileSystem: FileSystem
  private memoryManager: MemoryManager
  private perception: Perception
  private llmClient: GeminiClient
  private actionExecutor: ActionExecutor
  private voiceManager?: VoiceManager
  private clarificationAgent?: ClarificationAgent
  private dialogueManager?: DialogueManager

  // State
  private state: AgentState
  private history: AgentHistory[]
  private currentTask: string = ''
  private lastTapCoordinates?: { x: number; y: number }
  private triggerFlash: boolean = false

  constructor(config: AgentConfig) {
    this.config = config
    this.settings = {
      ...DEFAULT_SETTINGS,
      maxHistoryItems: config.maxSteps ? Math.min(config.maxSteps, 20) : 20,
    }

    // Initialize state
    this.state = {
      nSteps: 0,
      stopped: false,
      consecutiveFailures: 0,
      lastModelOutput: null,
      lastResult: null,
    }

    this.history = []

    // Initialize subsystems
    this.nativeModule = new AccessibilityModule()
    this.fileSystem = new FileSystem()
    this.memoryManager = new MemoryManager(this.fileSystem, this.settings)
    this.perception = new Perception(this.nativeModule, new SemanticParser())
    this.llmClient = new GeminiClient({
      apiKey: config.apiKey,
      model: config.model,
      maxRetries: this.settings.maxLLMRetries,
    })
    this.actionExecutor = new ActionExecutor(
      this.nativeModule,
      this.fileSystem,
      undefined, // VoiceManager will be initialized in run()
      undefined  // IntentRegistry not yet implemented
    )

    // Initialize clarification system if enabled
    if (config.enableClarification !== false) {
      this.clarificationAgent = new ClarificationAgent(this.llmClient, config.clarificationConfig)
      this.dialogueManager = new DialogueManager(this.clarificationAgent)
    }

    // Initialize voice manager if needed
    if (config.debugMode) {
      console.log('Agent initialized with config:', {
        maxSteps: config.maxSteps,
        maxFailures: config.maxFailures,
        debugMode: config.debugMode,
        enableClarification: config.enableClarification !== false,
      })
    }
  }

  /**
   * Run the agent with a given task
   * Executes SENSE → THINK → ACT loop until completion or termination
   * 
   * @param task - Natural language task description
   * @param maxSteps - Optional override for max steps
   * @returns AgentResult with success status, message, and history
   */
  async run(task: string, maxSteps?: number): Promise<AgentResult> {
    const effectiveMaxSteps = maxSteps || this.config.maxSteps || 100
    const maxFailures = this.config.maxFailures || 3

    try {
      // Initialize file system
      await this.fileSystem.initialize()

      // Initialize voice manager if available
      try {
        this.voiceManager = new VoiceManager()
        const voiceAvailable = this.voiceManager.isAvailable()
        if (voiceAvailable.both) {
          // Update action executor with voice manager
          this.actionExecutor = new ActionExecutor(
            this.nativeModule,
            this.fileSystem,
            this.voiceManager,
            undefined
          )
        }
      } catch (error) {
        console.warn('Voice manager initialization failed, continuing without voice:', error)
      }

      // ================================================================
      // PRE-EXECUTION CLARIFICATION PHASE
      // ================================================================
      let enhancedTask = task

      if (this.dialogueManager && this.config.enableClarification !== false) {
        if (this.config.debugMode) {
          console.log('Starting clarification phase...')
        }

        try {
          const dialogueState = await this.dialogueManager.startDialogue(task)

          // If clarification is needed and onClarificationNeeded callback is provided
          if (!dialogueState.isComplete && this.config.onClarificationNeeded) {
            // Notify that clarification is needed
            // The callback should handle the dialogue interaction
            await this.config.onClarificationNeeded(dialogueState)

            // After dialogue is complete, get enhanced instruction
            if (dialogueState.isComplete) {
              enhancedTask = await this.dialogueManager.completeDialogue(dialogueState.sessionId)
              
              if (this.config.debugMode) {
                console.log('Original task:', task)
                console.log('Enhanced task:', enhancedTask)
              }
            }
          } else if (dialogueState.enhancedInstruction) {
            // No clarification needed or already complete
            enhancedTask = dialogueState.enhancedInstruction
          }
        } catch (error) {
          console.warn('Clarification phase failed, using original task:', error)
          // Continue with original task on clarification failure
        }
      }

      // Initialize task in memory (use enhanced task if available)
      this.currentTask = enhancedTask
      this.memoryManager.addNewTask(enhancedTask)

      // Reset state for new run
      this.state = {
        nSteps: 0,
        stopped: false,
        consecutiveFailures: 0,
        lastModelOutput: null,
        lastResult: null,
      }
      this.history = []

      if (this.config.debugMode) {
        console.log(`Starting agent run with task: "${task}"`)
        console.log(`Max steps: ${effectiveMaxSteps}, Max failures: ${maxFailures}`)
      }

      // Main SENSE → THINK → ACT loop
      while (!this.state.stopped && this.state.nSteps < effectiveMaxSteps) {
        const stepStartTime = Date.now()
        this.state.nSteps++

        if (this.config.debugMode) {
          console.log(`\n=== Step ${this.state.nSteps}/${effectiveMaxSteps} ===`)
        }

        try {
          // ================================================================
          // SENSE: Analyze current screen state
          // ================================================================
          if (this.config.debugMode) {
            console.log('SENSE: Analyzing screen...')
          }

          const screenState = await this.perception.analyze()

          // Trigger flash on state change in debug mode
          if (this.config.debugMode) {
            this.triggerFlash = true
            console.log(`Found ${screenState.elementMap.size} interactive elements`)
            console.log(`Activity: ${screenState.activityName}`)
            console.log(`Keyboard: ${screenState.isKeyboardOpen ? 'open' : 'closed'}`)
          }

          // ================================================================
          // THINK: Get decision from LLM
          // ================================================================
          if (this.config.debugMode) {
            console.log('THINK: Consulting LLM...')
          }

          // Create state message with context
          await this.memoryManager.createStateMessage(
            this.state.lastModelOutput,
            this.state.lastResult,
            {
              stepNumber: this.state.nSteps,
              maxSteps: effectiveMaxSteps,
            },
            screenState
          )

          // Get messages for LLM
          const messages = this.memoryManager.getMessages()

          // Generate agent output
          const modelOutput = await this.llmClient.generateAgentOutput(messages)

          if (!modelOutput) {
            // LLM failed to generate valid output
            this.handleLLMFailure('Failed to generate valid output from LLM')
            continue
          }

          this.state.lastModelOutput = modelOutput

          if (this.config.debugMode) {
            console.log(`Next goal: ${modelOutput.nextGoal}`)
            console.log(`Actions: ${modelOutput.actions.length}`)
          }

          // ================================================================
          // ACT: Execute actions
          // ================================================================
          if (this.config.debugMode) {
            console.log('ACT: Executing actions...')
          }

          const actionResults: ActionResult[] = []
          let actionError: string | undefined

          for (let i = 0; i < modelOutput.actions.length; i++) {
            const action = modelOutput.actions[i]

            if (this.config.debugMode) {
              console.log(`  Action ${i + 1}/${modelOutput.actions.length}: ${action.type}`)
            }

            // Track tap coordinates for debug visualization
            if (this.config.debugMode && (action.type === 'tap_element' || action.type === 'long_press_element')) {
              const element = screenState.elementMap.get(action.elementId)
              if (element) {
                this.lastTapCoordinates = element.center
              }
            }

            try {
              const result = await this.actionExecutor.execute(action, screenState)
              actionResults.push(result)

              // Check if action failed
              if (result.error) {
                actionError = result.error
                if (this.config.debugMode) {
                  console.log(`  ❌ Action failed: ${result.error}`)
                }
                // Stop executing remaining actions on error
                break
              }

              // Check if done action
              if (result.isDone) {
                if (this.config.debugMode) {
                  console.log(`  ✓ Task marked as ${result.success ? 'complete' : 'failed'}`)
                }
                this.state.stopped = true
                break
              }

              if (this.config.debugMode) {
                console.log(`  ✓ Action completed`)
              }
            } catch (error) {
              const errorMessage = error instanceof Error ? error.message : String(error)
              actionError = errorMessage
              actionResults.push({ error: errorMessage })
              
              if (this.config.debugMode) {
                console.log(`  ❌ Action threw error: ${errorMessage}`)
              }
              break
            }
          }

          this.state.lastResult = actionResults

          // ================================================================
          // Update History and Memory
          // ================================================================
          
          // Record step in history
          const stepDuration = Date.now() - stepStartTime
          this.recordStep(modelOutput, actionResults, screenState, stepDuration)

          // Update memory manager history
          this.memoryManager.updateHistory(
            this.state.nSteps,
            modelOutput,
            actionResults,
            actionError
          )

          // ================================================================
          // Handle Errors and Failures
          // ================================================================
          
          if (actionError) {
            this.state.consecutiveFailures++
            
            if (this.config.debugMode) {
              console.log(`Consecutive failures: ${this.state.consecutiveFailures}/${maxFailures}`)
            }

            // Check if we've exceeded max failures
            if (this.state.consecutiveFailures >= maxFailures) {
              const failureMessage = `Exceeded maximum consecutive failures (${maxFailures})`
              
              if (this.voiceManager) {
                try {
                  await this.voiceManager.speak(failureMessage)
                } catch (voiceError) {
                  console.warn('Failed to speak failure message:', voiceError)
                }
              }

              throw new QueError(
                failureMessage,
                ErrorCategory.SYSTEM,
                false,
                { consecutiveFailures: this.state.consecutiveFailures }
              )
            }

            // Add corrective context for next iteration
            this.memoryManager.addContextMessage(
              `Previous action failed with error: ${actionError}. Please try a different approach or break down the task into smaller steps.`
            )
          } else {
            // Reset consecutive failures on success
            this.state.consecutiveFailures = 0
          }

          // ================================================================
          // Fire onStep Callback
          // ================================================================
          
          if (this.config.onStep) {
            const stepInfo: AgentStep = {
              stepNumber: this.state.nSteps,
              maxSteps: effectiveMaxSteps,
              modelOutput,
              actionResults,
              screenState,
              lastTapCoordinates: this.lastTapCoordinates,
              triggerFlash: this.triggerFlash,
            }
            this.config.onStep(stepInfo)
            
            // Reset debug feedback flags after callback
            this.triggerFlash = false
            this.lastTapCoordinates = undefined
          }

          // ================================================================
          // Check Termination Conditions
          // ================================================================
          
          // Check if done action was executed
          const doneResult = actionResults.find(r => r.isDone)
          if (doneResult) {
            const success = doneResult.success ?? false
            const message = doneResult.longTermMemory || (success ? 'Task completed successfully' : 'Task failed')
            
            if (this.config.debugMode) {
              console.log(`\n=== Task ${success ? 'Completed' : 'Failed'} ===`)
              console.log(message)
            }

            const result: AgentResult = {
              success,
              message,
              steps: this.state.nSteps,
              history: this.history,
              files: await this.fileSystem.listFiles(),
            }

            if (this.config.onComplete) {
              this.config.onComplete(result)
            }

            return result
          }

          // ================================================================
          // Wait Between Steps
          // ================================================================
          
          if (!this.state.stopped && this.state.nSteps < effectiveMaxSteps) {
            await this.delay(this.settings.stepDelay)
          }

        } catch (error) {
          // Handle step-level errors
          if (error instanceof QueError && !error.recoverable) {
            // Non-recoverable error, stop immediately
            throw error
          }

          // Recoverable error, increment failures and continue
          this.state.consecutiveFailures++
          
          const errorMessage = error instanceof Error ? error.message : String(error)
          
          if (this.config.debugMode) {
            console.error(`Step ${this.state.nSteps} error:`, errorMessage)
          }

          // Add error to history
          this.memoryManager.addSystemMessageToHistory(
            this.state.nSteps,
            `System error: ${errorMessage}`
          )

          // Check if exceeded max failures
          if (this.state.consecutiveFailures >= maxFailures) {
            throw new QueError(
              `Exceeded maximum consecutive failures (${maxFailures})`,
              ErrorCategory.SYSTEM,
              false,
              { lastError: error }
            )
          }

          // Add corrective context
          this.memoryManager.addContextMessage(
            `System encountered an error: ${errorMessage}. Please try a different approach.`
          )
        }
      }

      // ================================================================
      // Loop Ended Without Done Action
      // ================================================================
      
      const reachedMaxSteps = this.state.nSteps >= effectiveMaxSteps
      const message = reachedMaxSteps
        ? `Reached maximum steps (${effectiveMaxSteps}) without completing task`
        : 'Agent stopped before completion'

      if (this.config.debugMode) {
        console.log(`\n=== ${message} ===`)
      }

      const result: AgentResult = {
        success: false,
        message,
        steps: this.state.nSteps,
        history: this.history,
        files: await this.fileSystem.listFiles(),
      }

      if (this.config.onComplete) {
        this.config.onComplete(result)
      }

      return result

    } catch (error) {
      // Handle top-level errors
      const errorMessage = error instanceof Error ? error.message : String(error)
      
      if (this.config.debugMode) {
        console.error('Agent run failed:', errorMessage)
      }

      if (this.config.onError) {
        this.config.onError(error as Error)
      }

      const result: AgentResult = {
        success: false,
        message: `Agent failed: ${errorMessage}`,
        steps: this.state.nSteps,
        history: this.history,
        files: await this.fileSystem.listFiles().catch(() => []),
      }

      return result
    }
  }

  /**
   * Stop the agent manually
   * Sets stopped flag to terminate loop after current step
   */
  stop(): void {
    if (this.config.debugMode) {
      console.log('Agent stop requested')
    }
    this.state.stopped = true
  }

  /**
   * Get current agent state
   */
  getState(): AgentState {
    return { ...this.state }
  }

  /**
   * Get complete execution history
   */
  getHistory(): AgentHistory[] {
    return [...this.history]
  }

  /**
   * Get current task
   */
  getCurrentTask(): string {
    return this.currentTask
  }

  /**
   * Get file system instance
   */
  getFileSystem(): FileSystem {
    return this.fileSystem
  }

  /**
   * Get memory manager instance
   */
  getMemoryManager(): MemoryManager {
    return this.memoryManager
  }

  // ==========================================================================
  // Private Helper Methods
  // ==========================================================================

  /**
   * Record a step in the history
   */
  private recordStep(
    modelOutput: AgentOutput,
    actionResults: ActionResult[],
    screenState: ScreenState,
    duration: number
  ): void {
    const historyItem: AgentHistory = {
      modelOutput,
      result: actionResults,
      state: screenState,
      metadata: {
        timestamp: Date.now(),
        duration,
      },
    }

    this.history.push(historyItem)
  }

  /**
   * Handle LLM failure with error recovery
   */
  private handleLLMFailure(errorMessage: string): void {
    this.state.consecutiveFailures++
    
    if (this.config.debugMode) {
      console.error(`LLM failure: ${errorMessage}`)
      console.log(`Consecutive failures: ${this.state.consecutiveFailures}`)
    }

    // Add corrective context message
    this.memoryManager.addContextMessage(
      `The previous LLM response was invalid or could not be parsed. Please ensure you respond with valid JSON matching the AgentOutput schema.`
    )

    // Record in history
    this.memoryManager.addSystemMessageToHistory(
      this.state.nSteps,
      `LLM failure: ${errorMessage}`
    )
  }

  /**
   * Delay helper
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
}


/**
 * MemoryManager class for managing conversation history and building prompts
 * Maintains context across agent steps with history tracking and truncation
 */

import {
  LLMMessage,
  ScreenState,
  AgentOutput,
  ActionResult,
  HistoryItem,
  MemoryState,
  AgentSettings,
} from '../types'
import { FileSystem } from './FileSystem'
import { PromptBuilder } from './PromptBuilder'

export class MemoryManager {
  private fileSystem: FileSystem
  private promptBuilder: PromptBuilder
  private settings: AgentSettings
  private state: MemoryState
  private currentTask: string = ''

  constructor(fileSystem: FileSystem, settings: AgentSettings) {
    this.fileSystem = fileSystem
    this.settings = settings
    this.promptBuilder = new PromptBuilder(fileSystem, {
      maxHistoryItems: settings.maxHistoryItems,
      includeFileContent: true,
    })

    // Initialize memory state
    this.state = {
      agentHistoryItems: [],
      readStateDescription: '',
      history: {
        systemMessage: null,
        stateMessage: null,
        contextMessages: [],
      },
    }
  }

  /**
   * Initialize a new task
   * Resets history and creates system message
   */
  addNewTask(task: string): void {
    this.currentTask = task

    // Reset state for new task
    this.state = {
      agentHistoryItems: [],
      readStateDescription: '',
      history: {
        systemMessage: this.promptBuilder.buildSystemMessage(),
        stateMessage: null,
        contextMessages: [],
      },
    }
  }

  /**
   * Create state message for current step
   * Builds user message with all context and updates history
   */
  async createStateMessage(
    modelOutput: AgentOutput | null,
    result: ActionResult[] | null,
    stepInfo: { stepNumber: number; maxSteps: number },
    screenState: ScreenState
  ): Promise<void> {
    // Build user message with all context
    const userMessage = await this.promptBuilder.buildUserMessage(
      this.currentTask,
      screenState,
      stepInfo,
      modelOutput,
      result,
      this.state.agentHistoryItems,
      this.state.readStateDescription
    )

    // Update state message
    this.state.history.stateMessage = userMessage

    // Clear one-time read state after including it
    this.state.readStateDescription = ''
  }

  /**
   * Update history after a step completes
   * Tracks model output, results, and any errors
   */
  updateHistory(
    stepNumber: number,
    modelOutput: AgentOutput | null,
    result: ActionResult[] | null,
    error?: string
  ): void {
    const historyItem: HistoryItem = {
      stepNumber,
    }

    // Add model output fields if available
    if (modelOutput) {
      historyItem.evaluation = modelOutput.evaluationPreviousGoal
      historyItem.memory = modelOutput.memory
      historyItem.nextGoal = modelOutput.nextGoal
    }

    // Add action results if available
    if (result && result.length > 0) {
      historyItem.actionResults = this.formatActionResultsForHistory(result)

      // Extract content that should be added to read state
      for (const actionResult of result) {
        if (actionResult.extractedContent) {
          if (actionResult.includeExtractedContentOnlyOnce) {
            // Add to one-time read state
            this.addToReadState(actionResult.extractedContent)
          } else {
            // Add to long-term memory in history
            historyItem.actionResults += `\nExtracted: ${actionResult.extractedContent}`
          }
        }

        if (actionResult.longTermMemory) {
          // Add to history
          historyItem.actionResults += `\n${actionResult.longTermMemory}`
        }
      }
    }

    // Add error if present
    if (error) {
      historyItem.error = error
    }

    // Add to history
    this.state.agentHistoryItems.push(historyItem)

    // Truncate history if needed
    this.truncateHistory()
  }

  /**
   * Add a context message (for errors, corrections, user input)
   */
  addContextMessage(message: string): void {
    const contextMessage = this.promptBuilder.buildContextMessage(message)
    this.state.history.contextMessages.push(contextMessage)
  }

  /**
   * Add a system message to history items
   */
  addSystemMessageToHistory(stepNumber: number, message: string): void {
    const historyItem: HistoryItem = {
      stepNumber,
      systemMessage: message,
    }
    this.state.agentHistoryItems.push(historyItem)
    this.truncateHistory()
  }

  /**
   * Get all messages for LLM
   * Returns array of messages in order: system, context, state
   */
  getMessages(): LLMMessage[] {
    const messages: LLMMessage[] = []

    // Add system message
    if (this.state.history.systemMessage) {
      messages.push(this.state.history.systemMessage)
    }

    // Add context messages
    messages.push(...this.state.history.contextMessages)

    // Add current state message
    if (this.state.history.stateMessage) {
      messages.push(this.state.history.stateMessage)
    }

    return messages
  }

  /**
   * Get agent history items
   */
  getAgentHistory(): HistoryItem[] {
    return [...this.state.agentHistoryItems]
  }

  /**
   * Get current memory state
   */
  getMemoryState(): MemoryState {
    return { ...this.state }
  }

  /**
   * Clear context messages (useful after LLM processes them)
   */
  clearContextMessages(): void {
    this.state.history.contextMessages = []
  }

  /**
   * Add content to one-time read state
   * This content will be included once and then cleared
   */
  addToReadState(content: string): void {
    if (this.state.readStateDescription) {
      this.state.readStateDescription += '\n\n' + content
    } else {
      this.state.readStateDescription = content
    }
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

  // ============================================================================
  // Private Helper Methods
  // ============================================================================

  /**
   * Format action results for history tracking
   */
  private formatActionResultsForHistory(results: ActionResult[]): string {
    const parts: string[] = []

    for (let i = 0; i < results.length; i++) {
      const result = results[i]

      if (result.error) {
        parts.push(`Action ${i + 1}: Error - ${result.error}`)
      } else if (result.isDone) {
        parts.push(`Action ${i + 1}: Task marked as ${result.success ? 'complete' : 'failed'}`)
      } else {
        parts.push(`Action ${i + 1}: Success`)
      }
    }

    return parts.join('; ')
  }

  /**
   * Truncate history to max items
   * Keeps most recent items within the limit
   */
  private truncateHistory(): void {
    const maxItems = this.settings.maxHistoryItems

    if (this.state.agentHistoryItems.length > maxItems) {
      // Keep only the most recent items
      this.state.agentHistoryItems = this.state.agentHistoryItems.slice(-maxItems)
    }
  }
}

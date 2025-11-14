/**
 * PromptBuilder class for constructing system and user messages
 * Loads templates and builds context-aware prompts for the LLM
 */

import { LLMMessage, ScreenState, AgentOutput, ActionResult, HistoryItem } from '../types'
import { FileSystem } from './FileSystem'

// System prompt template - embedded for React Native compatibility
const SYSTEM_PROMPT_TEMPLATE = `# QUE Mobile Agent System Prompt

You are an AI agent that controls an Android device to accomplish user tasks. You operate in a SENSE → THINK → ACT loop:

1. **SENSE**: You receive the current screen state with interactive elements
2. **THINK**: You analyze the situation and plan the next actions
3. **ACT**: You execute actions to progress toward the goal

## Your Capabilities

You can perform these actions:

### Navigation & Interaction
- tap_element: Tap on an element by its ID
- long_press_element: Long press on an element by its ID
- tap_element_input_text_and_enter: Tap element, type text, and press enter
- type: Type text into the currently focused field
- swipe_down: Scroll down by specified pixels
- swipe_up: Scroll up by specified pixels
- back: Press the back button
- home: Go to home screen
- switch_app: Open app switcher
- wait: Wait for UI to update

### App Control
- open_app: Launch an app by name (e.g., "Chrome", "Settings")
- search_google: Search Google with a query

### Voice
- speak: Speak a message to the user via TTS
- ask: Ask the user a question and get voice response

### File Operations
- write_file: Create/overwrite a file (only .md or .txt)
- append_file: Append content to a file
- read_file: Read file contents

### Special
- launch_intent: Launch a custom Android intent
- done: Mark task as complete with success status

## Response Format

You must respond with valid JSON in this exact format:

{
  "evaluationPreviousGoal": "Brief evaluation of what happened in the last step",
  "memory": "Important information to remember for future steps",
  "nextGoal": "Clear description of what you're trying to accomplish next",
  "actions": [
    { "type": "action_type", ...parameters }
  ]
}

## Guidelines

1. Be Efficient: Accomplish tasks in the minimum number of steps
2. Be Precise: Use exact element IDs from the screen state
3. Be Patient: Wait for UI updates after actions (use wait action)
4. Be Adaptive: If an action fails, try alternative approaches
5. Be Thorough: Check that actions succeeded before proceeding
6. Use Memory: Track progress in files (todo.md, results.md)
7. Communicate: Use speak to inform users of progress
8. Finish Properly: Always use done action when task is complete

## Screen State Format

You'll receive screen state like this:

Activity: com.example.app/.MainActivity
Keyboard: closed
Scroll: ↑ 0px | ↓ 500px

Elements:
1. Button "Submit" [100,200][300,250] clickable
2. EditText "Enter name" [100,300][300,350] clickable
3. TextView "Welcome" [100,400][300,450]

- Element IDs are the numbers (1, 2, 3...)
- Bounds show [left,top][right,bottom] coordinates
- "clickable" means you can tap it
- Scroll indicators show available scroll space

## Error Handling

If an action fails:
1. Read the error message carefully
2. Adjust your approach
3. Try alternative elements or methods
4. If stuck after 3 failures, explain the issue and use done with success=false

## File System Usage

Use files to track complex tasks:
- todo.md: Break down tasks into steps
- results.md: Accumulate findings and results
- notes.md: Store important information

Read files at the start of each session to maintain context.

Now, accomplish the user's task efficiently and effectively!`

export interface PromptBuilderConfig {
  maxHistoryItems?: number
  includeFileContent?: boolean
}

export class PromptBuilder {
  private fileSystem: FileSystem | null
  private config: Required<PromptBuilderConfig>

  constructor(fileSystem: FileSystem | null = null, config: PromptBuilderConfig = {}) {
    this.fileSystem = fileSystem
    this.config = {
      maxHistoryItems: config.maxHistoryItems ?? 20,
      includeFileContent: config.includeFileContent ?? true,
    }
  }

  /**
   * Build system message with prompt template
   */
  buildSystemMessage(): LLMMessage {
    return {
      role: 'system',
      content: SYSTEM_PROMPT_TEMPLATE,
    }
  }

  /**
   * Build user message with task, screen state, and history
   */
  async buildUserMessage(
    task: string,
    screenState: ScreenState,
    stepInfo: { stepNumber: number; maxSteps: number },
    lastOutput: AgentOutput | null,
    lastResult: ActionResult[] | null,
    history: HistoryItem[],
    readStateDescription: string
  ): Promise<LLMMessage> {
    const parts: string[] = []

    // Add task description
    parts.push(`# Task\n${task}\n`)

    // Add step information
    parts.push(`# Step Information\nStep ${stepInfo.stepNumber} of ${stepInfo.maxSteps}\n`)

    // Add previous step evaluation if available
    if (lastOutput && lastResult) {
      parts.push(`# Previous Step\n`)
      parts.push(`Goal: ${lastOutput.nextGoal}\n`)
      parts.push(`Actions Executed: ${lastOutput.actions.length}\n`)
      
      const resultSummary = this.formatActionResults(lastResult)
      if (resultSummary) {
        parts.push(`Results:\n${resultSummary}\n`)
      }
    }

    // Add agent history (truncated)
    if (history.length > 0) {
      const truncatedHistory = this.truncateHistory(history)
      const historyText = this.formatHistory(truncatedHistory)
      parts.push(`# Recent History\n${historyText}\n`)
    }

    // Add file system content if available
    if (this.fileSystem && this.config.includeFileContent) {
      const fileContent = await this.getFileSystemContent()
      if (fileContent) {
        parts.push(`# File System\n${fileContent}\n`)
      }
    }

    // Add one-time read state (user responses, file contents)
    if (readStateDescription) {
      parts.push(`# Additional Context\n${readStateDescription}\n`)
    }

    // Add current screen state
    parts.push(`# Current Screen State\n`)
    parts.push(`Activity: ${screenState.activityName}\n`)
    parts.push(`Keyboard: ${screenState.isKeyboardOpen ? 'open' : 'closed'}\n`)
    
    // Add scroll indicators
    if (screenState.scrollUp > 0 || screenState.scrollDown > 0) {
      parts.push(`Scroll: ↑ ${screenState.scrollUp}px | ↓ ${screenState.scrollDown}px\n`)
    }
    
    parts.push(`\nElements:\n${screenState.uiRepresentation}\n`)

    return {
      role: 'user',
      content: parts.join('\n'),
    }
  }

  /**
   * Build a context message (for errors, corrections, etc.)
   */
  buildContextMessage(message: string): LLMMessage {
    return {
      role: 'user',
      content: message,
    }
  }

  // ============================================================================
  // Private Helper Methods
  // ============================================================================

  /**
   * Format action results into readable text
   */
  private formatActionResults(results: ActionResult[]): string {
    const lines: string[] = []

    for (let i = 0; i < results.length; i++) {
      const result = results[i]
      
      if (result.error) {
        lines.push(`  ${i + 1}. ❌ Error: ${result.error}`)
      } else if (result.longTermMemory) {
        lines.push(`  ${i + 1}. ✓ ${result.longTermMemory}`)
      } else if (result.extractedContent) {
        lines.push(`  ${i + 1}. ✓ Content: ${result.extractedContent.substring(0, 100)}...`)
      } else {
        lines.push(`  ${i + 1}. ✓ Success`)
      }
    }

    return lines.join('\n')
  }

  /**
   * Format history items into readable text
   */
  private formatHistory(history: HistoryItem[]): string {
    const lines: string[] = []

    for (const item of history) {
      lines.push(`Step ${item.stepNumber}:`)
      
      if (item.evaluation) {
        lines.push(`  Evaluation: ${item.evaluation}`)
      }
      
      if (item.memory) {
        lines.push(`  Memory: ${item.memory}`)
      }
      
      if (item.nextGoal) {
        lines.push(`  Goal: ${item.nextGoal}`)
      }
      
      if (item.actionResults) {
        lines.push(`  Results: ${item.actionResults}`)
      }
      
      if (item.error) {
        lines.push(`  Error: ${item.error}`)
      }
      
      if (item.systemMessage) {
        lines.push(`  System: ${item.systemMessage}`)
      }
      
      lines.push('') // Empty line between steps
    }

    return lines.join('\n')
  }

  /**
   * Truncate history to fit within max items
   */
  private truncateHistory(history: HistoryItem[]): HistoryItem[] {
    if (history.length <= this.config.maxHistoryItems) {
      return history
    }

    // Keep most recent items
    return history.slice(-this.config.maxHistoryItems)
  }

  /**
   * Get file system content for context
   */
  private async getFileSystemContent(): Promise<string> {
    if (!this.fileSystem || !this.fileSystem.isInitialized()) {
      return ''
    }

    try {
      const files = await this.fileSystem.listFiles()
      const parts: string[] = []

      // Read key files (todo.md, results.md, notes.md)
      const keyFiles = ['todo.md', 'results.md', 'notes.md']
      
      for (const fileName of keyFiles) {
        if (files.includes(fileName)) {
          try {
            const { content, lineCount } = await this.fileSystem.readFile(fileName)
            parts.push(`## ${fileName} (${lineCount} lines)\n${content}\n`)
          } catch (error) {
            // Skip files that can't be read
            continue
          }
        }
      }

      // List other files
      const otherFiles = files.filter(f => !keyFiles.includes(f))
      if (otherFiles.length > 0) {
        parts.push(`## Other Files\n${otherFiles.join(', ')}\n`)
      }

      return parts.join('\n')
    } catch (error) {
      // Return empty string if file system access fails
      return ''
    }
  }
}

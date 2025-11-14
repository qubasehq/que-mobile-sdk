/**
 * OutputParser - Validates and parses LLM responses into AgentOutput
 */

import { AgentOutput } from '../types'

/**
 * Valid action types for validation
 */
const VALID_ACTION_TYPES = [
  'tap_element',
  'long_press_element',
  'tap_element_input_text_and_enter',
  'type',
  'swipe_down',
  'swipe_up',
  'back',
  'home',
  'switch_app',
  'wait',
  'open_app',
  'search_google',
  'speak',
  'ask',
  'write_file',
  'append_file',
  'read_file',
  'launch_intent',
  'done',
] as const

/**
 * Parse JSON string to AgentOutput object with validation
 * @param jsonString - JSON string from LLM response
 * @returns Parsed AgentOutput or null if validation fails
 */
export function parseAgentOutput(jsonString: string): AgentOutput | null {
  try {
    // Parse JSON
    const parsed = JSON.parse(jsonString)

    // Validate required fields
    if (!isValidAgentOutput(parsed)) {
      console.error('Invalid AgentOutput structure:', parsed)
      return null
    }

    return parsed as AgentOutput
  } catch (error) {
    console.error('Failed to parse AgentOutput JSON:', error)
    return null
  }
}

/**
 * Validate that parsed object matches AgentOutput structure
 */
function isValidAgentOutput(obj: any): boolean {
  // Check required fields exist
  if (typeof obj !== 'object' || obj === null) {
    return false
  }

  if (typeof obj.evaluationPreviousGoal !== 'string') {
    console.error('Missing or invalid evaluationPreviousGoal')
    return false
  }

  if (typeof obj.memory !== 'string') {
    console.error('Missing or invalid memory')
    return false
  }

  if (typeof obj.nextGoal !== 'string') {
    console.error('Missing or invalid nextGoal')
    return false
  }

  if (!Array.isArray(obj.actions)) {
    console.error('Missing or invalid actions array')
    return false
  }

  // Validate each action
  for (let i = 0; i < obj.actions.length; i++) {
    if (!isValidAction(obj.actions[i])) {
      console.error(`Invalid action at index ${i}:`, obj.actions[i])
      return false
    }
  }

  return true
}

/**
 * Validate that an action has correct type and required parameters
 */
function isValidAction(action: any): boolean {
  if (typeof action !== 'object' || action === null) {
    return false
  }

  if (typeof action.type !== 'string') {
    console.error('Action missing type field')
    return false
  }

  // Check if action type is valid
  if (!VALID_ACTION_TYPES.includes(action.type as any)) {
    console.error(`Invalid action type: ${action.type}`)
    return false
  }

  // Validate action-specific parameters
  switch (action.type) {
    case 'tap_element':
    case 'long_press_element':
      return typeof action.elementId === 'number'

    case 'tap_element_input_text_and_enter':
      return typeof action.index === 'number' && typeof action.text === 'string'

    case 'type':
      return typeof action.text === 'string'

    case 'swipe_down':
    case 'swipe_up':
      return typeof action.amount === 'number'

    case 'back':
    case 'home':
    case 'switch_app':
    case 'wait':
      return true // No parameters required

    case 'open_app':
      return typeof action.appName === 'string'

    case 'search_google':
      return typeof action.query === 'string'

    case 'speak':
      return typeof action.message === 'string'

    case 'ask':
      return typeof action.question === 'string'

    case 'write_file':
    case 'append_file':
      return typeof action.fileName === 'string' && typeof action.content === 'string'

    case 'read_file':
      return typeof action.fileName === 'string'

    case 'launch_intent':
      return (
        typeof action.intentName === 'string' &&
        typeof action.parameters === 'object' &&
        action.parameters !== null
      )

    case 'done':
      return (
        typeof action.success === 'boolean' &&
        typeof action.text === 'string' &&
        (action.filesToDisplay === undefined || Array.isArray(action.filesToDisplay))
      )

    default:
      return false
  }
}

/**
 * Extract JSON from markdown code blocks if present
 * LLMs sometimes wrap JSON in ```json ... ``` blocks
 */
export function extractJsonFromMarkdown(text: string): string {
  // Try to extract from code block
  const codeBlockMatch = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/)
  if (codeBlockMatch) {
    return codeBlockMatch[1].trim()
  }

  // Return original text if no code block found
  return text.trim()
}

/**
 * Core type definitions for QUE Mobile SDK
 */

import { Action } from '../actions/types'

// ============================================================================
// Agent Configuration
// ============================================================================

export interface AgentConfig {
  /** Gemini API key for LLM communication */
  apiKey: string
  /** Maximum number of steps before stopping (default: 100) */
  maxSteps?: number
  /** Maximum consecutive failures before stopping (default: 3) */
  maxFailures?: number
  /** Enable debug mode with visual feedback (default: false) */
  debugMode?: boolean
  /** Gemini model to use (default: 'gemini-pro') */
  model?: string
  /** Enable clarification system for ambiguous instructions (default: true) */
  enableClarification?: boolean
  /** Clarification system configuration */
  clarificationConfig?: ClarificationConfig
  /** Callback fired on each step completion */
  onStep?: (step: AgentStep) => void
  /** Callback fired when agent completes */
  onComplete?: (result: AgentResult) => void
  /** Callback fired on errors */
  onError?: (error: Error) => void
  /** Callback fired when clarification is needed */
  onClarificationNeeded?: (dialogueState: DialogueState) => Promise<void>
}

// Import clarification types
import { ClarificationConfig, DialogueState } from '../clarification/types'

// ============================================================================
// Agent State
// ============================================================================

export interface AgentState {
  /** Current step number */
  nSteps: number
  /** Whether agent has been stopped */
  stopped: boolean
  /** Number of consecutive failures */
  consecutiveFailures: number
  /** Last model output from LLM */
  lastModelOutput: AgentOutput | null
  /** Last action results */
  lastResult: ActionResult[] | null
}

// ============================================================================
// Agent Result
// ============================================================================

export interface AgentResult {
  /** Whether the task completed successfully */
  success: boolean
  /** Result message or error description */
  message: string
  /** Number of steps executed */
  steps: number
  /** Complete execution history */
  history: AgentHistory[]
  /** Files created during execution */
  files: string[]
}

// ============================================================================
// Agent Step (for onStep callback)
// ============================================================================

export interface AgentStep {
  /** Current step number */
  stepNumber: number
  /** Maximum steps allowed */
  maxSteps: number
  /** Model output for this step */
  modelOutput: AgentOutput
  /** Action results for this step */
  actionResults: ActionResult[]
  /** Screen state at this step */
  screenState: ScreenState
  /** Last tap coordinates for debug visualization */
  lastTapCoordinates?: { x: number; y: number }
  /** Trigger screen flash for debug visualization */
  triggerFlash?: boolean
}

// ============================================================================
// Screen State
// ============================================================================

export interface ScreenState {
  /** Formatted UI representation string for LLM */
  uiRepresentation: string
  /** Whether keyboard is currently open */
  isKeyboardOpen: boolean
  /** Current activity name */
  activityName: string
  /** Map of element IDs to element objects */
  elementMap: Map<number, Element>
  /** Pixels available to scroll up (0 if at top) */
  scrollUp: number
  /** Pixels available to scroll down (0 if at bottom) */
  scrollDown: number
}

// ============================================================================
// Element
// ============================================================================

export interface Element {
  /** Unique numeric ID for this element */
  id: number
  /** Human-readable description of element */
  description: string
  /** Bounds string in format "[left,top][right,bottom]" */
  bounds: string
  /** Center coordinates for tapping */
  center: { x: number; y: number }
  /** Whether element is clickable */
  isClickable: boolean
  /** Android resource ID (optional) */
  resourceId?: string
  /** Android class name (optional) */
  className?: string
  /** Text content (optional) */
  text?: string
}

// ============================================================================
// Action Result
// ============================================================================

export interface ActionResult {
  /** Content to add to long-term memory */
  longTermMemory?: string
  /** Content extracted from action (e.g., file contents) */
  extractedContent?: string
  /** Whether extracted content should only be included once */
  includeExtractedContentOnlyOnce?: boolean
  /** Error message if action failed */
  error?: string
  /** Whether this is a done action */
  isDone?: boolean
  /** Whether done action was successful */
  success?: boolean
  /** File attachments from done action */
  attachments?: string[]
}

// ============================================================================
// Agent Output (from LLM)
// ============================================================================

export interface AgentOutput {
  /** Evaluation of previous goal/step */
  evaluationPreviousGoal: string
  /** Information to remember for future steps */
  memory: string
  /** Next goal to accomplish */
  nextGoal: string
  /** Actions to execute */
  actions: Action[]
}

// ============================================================================
// Agent History
// ============================================================================

export interface AgentHistory {
  /** Model output for this step */
  modelOutput: AgentOutput
  /** Action results for this step */
  result: ActionResult[]
  /** Screen state at this step */
  state: ScreenState
  /** Optional metadata */
  metadata?: {
    /** Timestamp of step execution */
    timestamp: number
    /** Duration of step in milliseconds */
    duration: number
    /** Token count (if available) */
    tokens?: number
  }
}

// ============================================================================
// History Item (for MemoryManager)
// ============================================================================

export interface HistoryItem {
  /** Step number */
  stepNumber: number
  /** Evaluation from model output */
  evaluation?: string
  /** Memory from model output */
  memory?: string
  /** Next goal from model output */
  nextGoal?: string
  /** Formatted action results */
  actionResults?: string
  /** Error message if step failed */
  error?: string
  /** System message if added */
  systemMessage?: string
}

// ============================================================================
// LLM Message
// ============================================================================

export interface LLMMessage {
  /** Message role */
  role: 'system' | 'user' | 'assistant'
  /** Message content */
  content: string
}

// ============================================================================
// Agent Settings (internal configuration)
// ============================================================================

export interface AgentSettings {
  /** Maximum history items to keep in memory */
  maxHistoryItems: number
  /** Delay between steps in milliseconds */
  stepDelay: number
  /** Maximum retries for LLM failures */
  maxLLMRetries: number
  /** Timeout for actions in milliseconds */
  actionTimeout: number
}

// ============================================================================
// Memory State (for MemoryManager)
// ============================================================================

export interface MemoryState {
  /** Agent history items */
  agentHistoryItems: HistoryItem[]
  /** Read state description (one-time content) */
  readStateDescription: string
  /** Message history for LLM */
  history: {
    /** System prompt message */
    systemMessage: LLMMessage | null
    /** Current state message */
    stateMessage: LLMMessage | null
    /** Additional context messages */
    contextMessages: LLMMessage[]
  }
}

// ============================================================================
// Screen Dimensions
// ============================================================================

export interface ScreenDimensions {
  /** Screen width in pixels */
  width: number
  /** Screen height in pixels */
  height: number
}

// ============================================================================
// Scroll Info
// ============================================================================

export interface ScrollInfo {
  /** Pixels available to scroll up */
  pixelsAbove: number
  /** Pixels available to scroll down */
  pixelsBelow: number
}

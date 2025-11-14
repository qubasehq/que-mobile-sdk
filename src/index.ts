/**
 * QUE Mobile SDK - Main Package Exports
 * 
 * This is the main entry point for the QUE Mobile SDK.
 * It provides AI-powered Android automation through a clean TypeScript API.
 * 
 * @packageDocumentation
 */

// ============================================================================
// Core Agent
// ============================================================================

export { Agent } from './core'

// ============================================================================
// React Hooks
// ============================================================================

export { useAgent } from './hooks'
export type { UseAgentReturn } from './hooks'

export { useVoice } from './hooks'
export type { UseVoiceReturn } from './hooks'

export { useFileSystem } from './hooks'
export type { UseFileSystemReturn } from './hooks'

// ============================================================================
// React Components
// ============================================================================

export { AgentProvider, useAgentContext } from './components'
export type { AgentProviderProps, AgentContextValue } from './components'

export { AgentButton } from './components'
export type { AgentButtonProps } from './components'

export { DebugOverlay } from './components'
export type { DebugOverlayProps } from './components'

export { DebugVisualFeedback } from './components'
export type { DebugVisualFeedbackProps } from './components'

export { VoiceWaveAnimation } from './components'

export { VoiceWaveAnimationAdvanced } from './components'

// ============================================================================
// Action Types (All 19+ action types)
// ============================================================================

export type {
  Action,
  TapElementAction,
  LongPressElementAction,
  TapElementInputTextAndEnterAction,
  TypeAction,
  SwipeDownAction,
  SwipeUpAction,
  BackAction,
  HomeAction,
  SwitchAppAction,
  WaitAction,
  OpenAppAction,
  SearchGoogleAction,
  SpeakAction,
  AskAction,
  WriteFileAction,
  AppendFileAction,
  ReadFileAction,
  LaunchIntentAction,
  DoneAction,
  TakeScreenshotAction,
  GetClipboardAction,
  SetClipboardAction,
  GetInstalledAppsAction,
  GetCurrentAppAction,
  SendNotificationAction,
  ListFilesAction,
  DeleteFileAction,
  GenerateToolAction,
  ExecuteDynamicToolAction,
} from './actions/types'

// Action creator functions
export {
  createTapElementAction,
  createLongPressElementAction,
  createTapElementInputTextAndEnterAction,
  createTypeAction,
  createSwipeDownAction,
  createSwipeUpAction,
  createBackAction,
  createHomeAction,
  createSwitchAppAction,
  createWaitAction,
  createOpenAppAction,
  createSearchGoogleAction,
  createSpeakAction,
  createAskAction,
  createWriteFileAction,
  createAppendFileAction,
  createReadFileAction,
  createLaunchIntentAction,
  createDoneAction,
  createTakeScreenshotAction,
  createGetClipboardAction,
  createSetClipboardAction,
  createGetInstalledAppsAction,
  createGetCurrentAppAction,
  createSendNotificationAction,
  createListFilesAction,
  createDeleteFileAction,
  createGenerateToolAction,
  createExecuteDynamicToolAction,
} from './actions/types'

// Action type guards
export {
  isTapElementAction,
  isLongPressElementAction,
  isTapElementInputTextAndEnterAction,
  isTypeAction,
  isSwipeDownAction,
  isSwipeUpAction,
  isBackAction,
  isHomeAction,
  isSwitchAppAction,
  isWaitAction,
  isOpenAppAction,
  isSearchGoogleAction,
  isSpeakAction,
  isAskAction,
  isWriteFileAction,
  isAppendFileAction,
  isReadFileAction,
  isLaunchIntentAction,
  isDoneAction,
} from './actions/types'

// ============================================================================
// Core Type Definitions
// ============================================================================

export type {
  AgentConfig,
  AgentState,
  AgentResult,
  AgentStep,
  AgentHistory,
  AgentSettings,
  ScreenState,
  Element,
  ActionResult,
  AgentOutput,
  HistoryItem,
  LLMMessage,
  MemoryState,
  ScreenDimensions,
  ScrollInfo,
} from './types'

// ============================================================================
// Error Classes and Types
// ============================================================================

export {
  QueError,
  NativeModuleError,
  AccessibilityServiceError,
  LLMError,
  LLMParseError,
  ActionExecutionError,
  ElementNotFoundError,
  MaxStepsError,
  MaxFailuresError,
  FileSystemError,
  VoiceError,
  ErrorCategory,
} from './utils/errors'

// Error helper functions
export {
  isQueError,
  isRecoverableError,
  getErrorCategory,
  toQueError,
  formatErrorForLLM,
  createRecoveryMessage,
} from './utils/errors'

// ============================================================================
// Action Execution
// ============================================================================

export { ActionExecutor } from './actions'
export { ActionRetryHandler } from './actions'
export { DynamicToolGenerator } from './actions'
export { ToolRegistry } from './actions'
export { MCPToolAdapter } from './actions'

// ============================================================================
// Perception System
// ============================================================================

export { Perception } from './perception'
export { SemanticParser } from './perception'

// ============================================================================
// Memory Management
// ============================================================================

export { FileSystem } from './memory'
export { PromptBuilder } from './memory'
export type { PromptBuilderConfig } from './memory'
export { MemoryManager } from './memory'

// ============================================================================
// LLM Client
// ============================================================================

export { GeminiClient } from './llm'
export type { GeminiClientConfig } from './llm'
export { parseAgentOutput, extractJsonFromMarkdown } from './llm'

// ============================================================================
// Voice Manager
// ============================================================================

export { VoiceManager, getVoiceManager, destroyVoiceManager } from './voice'

// ============================================================================
// Trigger System
// ============================================================================

export { TriggerManager } from './triggers'
export { ScheduleTrigger } from './triggers'
export { NotificationTrigger } from './triggers'
export { TriggerExecutor } from './triggers'

export type {
  TriggerConfig,
  ScheduleTriggerConfig,
  NotificationTriggerConfig,
  ScheduleConfig,
  NotificationConfig,
  TriggerStatus,
  TriggerExecutionEvent,
  TriggerHistoryItem,
  BaseTriggerConfig,
} from './triggers'

export { useTriggers } from './hooks'
export type { UseTriggers, UseTriggersConfig } from './hooks'

// ============================================================================
// Clarification System
// ============================================================================

export { ClarificationAgent } from './clarification'
export { DialogueManager } from './clarification'
export { InstructionEnhancer } from './clarification'

export type {
  AmbiguityAnalysis,
  AmbiguousElement,
  ClarificationQuestion,
  ClarificationResponse,
  ClarificationConfig,
  DialogueState,
} from './clarification'

export { useClarification } from './hooks'
export type { UseClarificationConfig, UseClarificationResult } from './hooks'

// ============================================================================
// Native Module
// ============================================================================

export { AccessibilityModule, accessibilityModule } from './native/AccessibilityModule'

// ============================================================================
// Re-export everything for convenience (wildcard exports)
// ============================================================================

// This allows users to import anything not explicitly listed above
// while maintaining explicit exports for better documentation and tree-shaking

export * from './core'
export * from './hooks'
export * from './components'
export * from './actions/types'
export * from './types'
export * from './utils/errors'
export * from './actions'
export * from './perception'
export * from './memory'
export * from './llm'
export * from './voice'
export * from './triggers'
export * from './clarification'

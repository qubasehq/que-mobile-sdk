/**
 * Action type definitions for QUE Mobile SDK
 * All actions use discriminated unions for type safety
 */

// ============================================================================
// Action Interfaces (19 total action types)
// ============================================================================

export interface TapElementAction {
  type: 'tap_element'
  elementId: number
}

export interface LongPressElementAction {
  type: 'long_press_element'
  elementId: number
}

export interface TapElementInputTextAndEnterAction {
  type: 'tap_element_input_text_and_enter'
  index: number
  text: string
}

export interface TypeAction {
  type: 'type'
  text: string
}

export interface SwipeDownAction {
  type: 'swipe_down'
  amount: number
}

export interface SwipeUpAction {
  type: 'swipe_up'
  amount: number
}

export interface BackAction {
  type: 'back'
}

export interface HomeAction {
  type: 'home'
}

export interface SwitchAppAction {
  type: 'switch_app'
}

export interface WaitAction {
  type: 'wait'
}

export interface OpenAppAction {
  type: 'open_app'
  appName: string
}

export interface SearchGoogleAction {
  type: 'search_google'
  query: string
}

export interface SpeakAction {
  type: 'speak'
  message: string
}

export interface AskAction {
  type: 'ask'
  question: string
}

export interface WriteFileAction {
  type: 'write_file'
  fileName: string
  content: string
}

export interface AppendFileAction {
  type: 'append_file'
  fileName: string
  content: string
}

export interface ReadFileAction {
  type: 'read_file'
  fileName: string
}

export interface LaunchIntentAction {
  type: 'launch_intent'
  intentName: string
  parameters: Record<string, string>
}

export interface DoneAction {
  type: 'done'
  success: boolean
  text: string
  filesToDisplay?: string[]
}

export interface TakeScreenshotAction {
  type: 'take_screenshot'
  fileName?: string
}

export interface GetClipboardAction {
  type: 'get_clipboard'
}

export interface SetClipboardAction {
  type: 'set_clipboard'
  text: string
}

export interface GetInstalledAppsAction {
  type: 'get_installed_apps'
}

export interface GetCurrentAppAction {
  type: 'get_current_app'
}

export interface SendNotificationAction {
  type: 'send_notification'
  title: string
  message: string
}

export interface ListFilesAction {
  type: 'list_files'
}

export interface DeleteFileAction {
  type: 'delete_file'
  fileName: string
}

export interface GenerateToolAction {
  type: 'generate_tool'
  toolName: string
  description: string
  parameters: Array<{
    name: string
    type: 'string' | 'number' | 'boolean' | 'object'
    description: string
    required: boolean
  }>
  intent: string
}

export interface ExecuteDynamicToolAction {
  type: 'execute_dynamic_tool'
  toolName: string
  parameters: Record<string, any>
}

// ============================================================================
// Action Union Type (Discriminated Union)
// ============================================================================

export type Action =
  | TapElementAction
  | LongPressElementAction
  | TapElementInputTextAndEnterAction
  | TypeAction
  | SwipeDownAction
  | SwipeUpAction
  | BackAction
  | HomeAction
  | SwitchAppAction
  | WaitAction
  | OpenAppAction
  | SearchGoogleAction
  | SpeakAction
  | AskAction
  | WriteFileAction
  | AppendFileAction
  | ReadFileAction
  | LaunchIntentAction
  | DoneAction
  | TakeScreenshotAction
  | GetClipboardAction
  | SetClipboardAction
  | GetInstalledAppsAction
  | GetCurrentAppAction
  | SendNotificationAction
  | ListFilesAction
  | DeleteFileAction
  | GenerateToolAction
  | ExecuteDynamicToolAction

// ============================================================================
// Action Creator Functions (Type-safe action builders)
// ============================================================================

export const createTapElementAction = (elementId: number): TapElementAction => ({
  type: 'tap_element',
  elementId,
})

export const createLongPressElementAction = (elementId: number): LongPressElementAction => ({
  type: 'long_press_element',
  elementId,
})

export const createTapElementInputTextAndEnterAction = (
  index: number,
  text: string
): TapElementInputTextAndEnterAction => ({
  type: 'tap_element_input_text_and_enter',
  index,
  text,
})

export const createTypeAction = (text: string): TypeAction => ({
  type: 'type',
  text,
})

export const createSwipeDownAction = (amount: number): SwipeDownAction => ({
  type: 'swipe_down',
  amount,
})

export const createSwipeUpAction = (amount: number): SwipeUpAction => ({
  type: 'swipe_up',
  amount,
})

export const createBackAction = (): BackAction => ({
  type: 'back',
})

export const createHomeAction = (): HomeAction => ({
  type: 'home',
})

export const createSwitchAppAction = (): SwitchAppAction => ({
  type: 'switch_app',
})

export const createWaitAction = (): WaitAction => ({
  type: 'wait',
})

export const createOpenAppAction = (appName: string): OpenAppAction => ({
  type: 'open_app',
  appName,
})

export const createSearchGoogleAction = (query: string): SearchGoogleAction => ({
  type: 'search_google',
  query,
})

export const createSpeakAction = (message: string): SpeakAction => ({
  type: 'speak',
  message,
})

export const createAskAction = (question: string): AskAction => ({
  type: 'ask',
  question,
})

export const createWriteFileAction = (fileName: string, content: string): WriteFileAction => ({
  type: 'write_file',
  fileName,
  content,
})

export const createAppendFileAction = (fileName: string, content: string): AppendFileAction => ({
  type: 'append_file',
  fileName,
  content,
})

export const createReadFileAction = (fileName: string): ReadFileAction => ({
  type: 'read_file',
  fileName,
})

export const createLaunchIntentAction = (
  intentName: string,
  parameters: Record<string, string>
): LaunchIntentAction => ({
  type: 'launch_intent',
  intentName,
  parameters,
})

export const createDoneAction = (
  success: boolean,
  text: string,
  filesToDisplay?: string[]
): DoneAction => ({
  type: 'done',
  success,
  text,
  filesToDisplay,
})

export const createTakeScreenshotAction = (fileName?: string): TakeScreenshotAction => ({
  type: 'take_screenshot',
  fileName,
})

export const createGetClipboardAction = (): GetClipboardAction => ({
  type: 'get_clipboard',
})

export const createSetClipboardAction = (text: string): SetClipboardAction => ({
  type: 'set_clipboard',
  text,
})

export const createGetInstalledAppsAction = (): GetInstalledAppsAction => ({
  type: 'get_installed_apps',
})

export const createGetCurrentAppAction = (): GetCurrentAppAction => ({
  type: 'get_current_app',
})

export const createSendNotificationAction = (
  title: string,
  message: string
): SendNotificationAction => ({
  type: 'send_notification',
  title,
  message,
})

export const createListFilesAction = (): ListFilesAction => ({
  type: 'list_files',
})

export const createDeleteFileAction = (fileName: string): DeleteFileAction => ({
  type: 'delete_file',
  fileName,
})

export const createGenerateToolAction = (
  toolName: string,
  description: string,
  parameters: Array<{
    name: string
    type: 'string' | 'number' | 'boolean' | 'object'
    description: string
    required: boolean
  }>,
  intent: string
): GenerateToolAction => ({
  type: 'generate_tool',
  toolName,
  description,
  parameters,
  intent,
})

export const createExecuteDynamicToolAction = (
  toolName: string,
  parameters: Record<string, any>
): ExecuteDynamicToolAction => ({
  type: 'execute_dynamic_tool',
  toolName,
  parameters,
})

// ============================================================================
// Action Type Guards (Runtime type checking)
// ============================================================================

export const isTapElementAction = (action: Action): action is TapElementAction =>
  action.type === 'tap_element'

export const isLongPressElementAction = (action: Action): action is LongPressElementAction =>
  action.type === 'long_press_element'

export const isTapElementInputTextAndEnterAction = (
  action: Action
): action is TapElementInputTextAndEnterAction => action.type === 'tap_element_input_text_and_enter'

export const isTypeAction = (action: Action): action is TypeAction => action.type === 'type'

export const isSwipeDownAction = (action: Action): action is SwipeDownAction =>
  action.type === 'swipe_down'

export const isSwipeUpAction = (action: Action): action is SwipeUpAction =>
  action.type === 'swipe_up'

export const isBackAction = (action: Action): action is BackAction => action.type === 'back'

export const isHomeAction = (action: Action): action is HomeAction => action.type === 'home'

export const isSwitchAppAction = (action: Action): action is SwitchAppAction =>
  action.type === 'switch_app'

export const isWaitAction = (action: Action): action is WaitAction => action.type === 'wait'

export const isOpenAppAction = (action: Action): action is OpenAppAction =>
  action.type === 'open_app'

export const isSearchGoogleAction = (action: Action): action is SearchGoogleAction =>
  action.type === 'search_google'

export const isSpeakAction = (action: Action): action is SpeakAction => action.type === 'speak'

export const isAskAction = (action: Action): action is AskAction => action.type === 'ask'

export const isWriteFileAction = (action: Action): action is WriteFileAction =>
  action.type === 'write_file'

export const isAppendFileAction = (action: Action): action is AppendFileAction =>
  action.type === 'append_file'

export const isReadFileAction = (action: Action): action is ReadFileAction =>
  action.type === 'read_file'

export const isLaunchIntentAction = (action: Action): action is LaunchIntentAction =>
  action.type === 'launch_intent'

export const isDoneAction = (action: Action): action is DoneAction => action.type === 'done'

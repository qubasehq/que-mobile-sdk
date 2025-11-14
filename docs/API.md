# QUE Mobile SDK - API Documentation

Complete API reference for all exported types, interfaces, and functions.

## Table of Contents

- [Core Types](#core-types)
- [Action Types](#action-types)
- [Hooks](#hooks)
- [Components](#components)
- [Error Handling](#error-handling)
- [Native Module](#native-module)

## Core Types

### AgentConfig

Configuration object for the Agent.

```typescript
interface AgentConfig {
  // Required
  apiKey: string;

  // Optional
  maxSteps?: number;        // Default: 100
  maxFailures?: number;     // Default: 3
  debugMode?: boolean;      // Default: false
  model?: string;           // Default: 'gemini-2.0-flash-exp'

  // Callbacks
  onStep?: (step: AgentStep) => void;
  onComplete?: (result: AgentResult) => void;
  onError?: (error: Error) => void;
}
```

**Properties:**

- `apiKey` (required): Your Gemini API key from Google AI Studio
- `maxSteps`: Maximum number of SENSE→THINK→ACT iterations before stopping
- `maxFailures`: Maximum consecutive action failures before stopping
- `debugMode`: Enable visual debugging features (element boxes, tap indicators)
- `model`: Gemini model to use for decision-making
- `onStep`: Callback invoked after each step with step details
- `onComplete`: Callback invoked when task completes
- `onError`: Callback invoked on errors

**Example:**

```typescript
const config: AgentConfig = {
  apiKey: process.env.GEMINI_API_KEY!,
  maxSteps: 50,
  maxFailures: 2,
  debugMode: true,
  onStep: (step) => {
    console.log(`Step ${step.stepNumber}: ${step.action}`);
  },
};
```

### AgentResult

Result returned after task execution.

```typescript
interface AgentResult {
  success: boolean;
  message: string;
  steps: number;
  history: AgentHistory[];
  files: string[];
}
```


**Properties:**

- `success`: Whether the task completed successfully
- `message`: Human-readable result message
- `steps`: Number of steps executed
- `history`: Complete execution history with all steps
- `files`: List of files created during execution

**Example:**

```typescript
const result = await execute("Open Settings");
if (result.success) {
  console.log(`Task completed in ${result.steps} steps`);
  console.log(`Files created: ${result.files.join(', ')}`);
}
```

### AgentState

Current state of the agent during execution.

```typescript
interface AgentState {
  nSteps: number;
  stopped: boolean;
  consecutiveFailures: number;
  lastModelOutput: AgentOutput | null;
  lastResult: ActionResult[] | null;
}
```

**Properties:**

- `nSteps`: Current step number
- `stopped`: Whether agent has been manually stopped
- `consecutiveFailures`: Count of consecutive failed actions
- `lastModelOutput`: Last output from LLM
- `lastResult`: Results from last action execution

### ScreenState

Represents the current screen state captured by Perception system.

```typescript
interface ScreenState {
  uiRepresentation: string;
  isKeyboardOpen: boolean;
  activityName: string;
  elementMap: Map<number, Element>;
  scrollUp: number;
  scrollDown: number;
}
```

**Properties:**

- `uiRepresentation`: Formatted string representation of UI hierarchy
- `isKeyboardOpen`: Whether soft keyboard is visible
- `activityName`: Current Android activity name
- `elementMap`: Map of element IDs to Element objects
- `scrollUp`: Pixels available to scroll up
- `scrollDown`: Pixels available to scroll down

### Element

Represents a UI element on the screen.

```typescript
interface Element {
  id: number;
  description: string;
  bounds: string;
  center: { x: number; y: number };
  isClickable: boolean;
  resourceId?: string;
  className?: string;
  text?: string;
}
```


**Properties:**

- `id`: Unique numeric identifier for this element
- `description`: Human-readable description combining text and resource ID
- `bounds`: Bounding box as string "[left,top][right,bottom]"
- `center`: Center coordinates for tapping
- `isClickable`: Whether element can be clicked
- `resourceId`: Android resource ID (e.g., "com.app:id/button")
- `className`: Android class name (e.g., "android.widget.Button")
- `text`: Visible text content

### AgentOutput

Output from the LLM after THINK phase.

```typescript
interface AgentOutput {
  evaluationPreviousGoal: string;
  memory: string;
  nextGoal: string;
  actions: Action[];
}
```

**Properties:**

- `evaluationPreviousGoal`: Evaluation of previous step's outcome
- `memory`: Important information to remember
- `nextGoal`: What the agent plans to do next
- `actions`: Array of actions to execute

### ActionResult

Result from executing a single action.

```typescript
interface ActionResult {
  longTermMemory?: string;
  extractedContent?: string;
  includeExtractedContentOnlyOnce?: boolean;
  error?: string;
  isDone?: boolean;
  success?: boolean;
  attachments?: string[];
}
```

**Properties:**

- `longTermMemory`: Information to persist in memory
- `extractedContent`: Content extracted from action (e.g., file contents)
- `includeExtractedContentOnlyOnce`: Whether to show extracted content only once
- `error`: Error message if action failed
- `isDone`: Whether this was a "done" action
- `success`: Whether action succeeded
- `attachments`: Files to display with result

## Action Types

All 19+ action types with examples and parameters.

### Navigation Actions

#### tap_element

Tap on a UI element by its ID.

```typescript
interface TapElementAction {
  type: 'tap_element';
  elementId: number;
}
```

**Example:**

```typescript
const action = createTapElementAction(5);
// { type: 'tap_element', elementId: 5 }
```


#### long_press_element

Long press on a UI element.

```typescript
interface LongPressElementAction {
  type: 'long_press_element';
  elementId: number;
}
```

**Example:**

```typescript
const action = createLongPressElementAction(3);
// Opens context menu on element 3
```

#### tap_element_input_text_and_enter

Tap element, input text, and press enter.

```typescript
interface TapElementInputTextAndEnterAction {
  type: 'tap_element_input_text_and_enter';
  index: number;
  text: string;
}
```

**Example:**

```typescript
const action = createTapElementInputTextAndEnterAction(2, "Hello World");
// Taps element 2, types "Hello World", presses enter
```

### Input Actions

#### type

Type text into the currently focused field.

```typescript
interface TypeAction {
  type: 'type';
  text: string;
}
```

**Example:**

```typescript
const action = createTypeAction("user@example.com");
// Types email into focused input field
```

### Gesture Actions

#### swipe_down

Swipe down by specified pixel amount.

```typescript
interface SwipeDownAction {
  type: 'swipe_down';
  amount: number;
}
```

**Example:**

```typescript
const action = createSwipeDownAction(500);
// Scrolls down 500 pixels
```

#### swipe_up

Swipe up by specified pixel amount.

```typescript
interface SwipeUpAction {
  type: 'swipe_up';
  amount: number;
}
```

**Example:**

```typescript
const action = createSwipeUpAction(300);
// Scrolls up 300 pixels
```


### System Actions

#### back

Press the back button.

```typescript
interface BackAction {
  type: 'back';
}
```

**Example:**

```typescript
const action = createBackAction();
// Navigates back
```

#### home

Press the home button.

```typescript
interface HomeAction {
  type: 'home';
}
```

**Example:**

```typescript
const action = createHomeAction();
// Returns to home screen
```

#### switch_app

Open the app switcher (recent apps).

```typescript
interface SwitchAppAction {
  type: 'switch_app';
}
```

**Example:**

```typescript
const action = createSwitchAppAction();
// Opens recent apps view
```

#### wait

Wait for 1 second.

```typescript
interface WaitAction {
  type: 'wait';
}
```

**Example:**

```typescript
const action = createWaitAction();
// Pauses for 1 second to let UI update
```

### App Actions

#### open_app

Open an app by name.

```typescript
interface OpenAppAction {
  type: 'open_app';
  appName: string;
}
```

**Example:**

```typescript
const action = createOpenAppAction("Instagram");
// Opens Instagram app
```


#### search_google

Search on Google.

```typescript
interface SearchGoogleAction {
  type: 'search_google';
  query: string;
}
```

**Example:**

```typescript
const action = createSearchGoogleAction("React Native tutorials");
// Opens Google and searches for "React Native tutorials"
```

### Voice Actions

#### speak

Convert text to speech.

```typescript
interface SpeakAction {
  type: 'speak';
  message: string;
}
```

**Example:**

```typescript
const action = createSpeakAction("Task completed successfully");
// Speaks the message using TTS
```

#### ask

Ask user a question via voice and get response.

```typescript
interface AskAction {
  type: 'ask';
  question: string;
}
```

**Example:**

```typescript
const action = createAskAction("What is your name?");
// Speaks question and listens for user response
```

### File Actions

#### write_file

Write content to a file.

```typescript
interface WriteFileAction {
  type: 'write_file';
  fileName: string;
  content: string;
}
```

**Example:**

```typescript
const action = createWriteFileAction("notes.md", "# Meeting Notes\n\n- Item 1\n- Item 2");
// Creates/overwrites notes.md with content
```

**Note:** Only `.md` and `.txt` extensions are allowed.


#### append_file

Append content to an existing file.

```typescript
interface AppendFileAction {
  type: 'append_file';
  fileName: string;
  content: string;
}
```

**Example:**

```typescript
const action = createAppendFileAction("notes.md", "\n- Item 3");
// Adds new item to existing notes.md
```

#### read_file

Read content from a file.

```typescript
interface ReadFileAction {
  type: 'read_file';
  fileName: string;
}
```

**Example:**

```typescript
const action = createReadFileAction("notes.md");
// Reads notes.md and includes content in agent memory
```

#### list_files

List all files in workspace.

```typescript
interface ListFilesAction {
  type: 'list_files';
}
```

**Example:**

```typescript
const action = createListFilesAction();
// Returns array of file names
```

#### delete_file

Delete a file from workspace.

```typescript
interface DeleteFileAction {
  type: 'delete_file';
  fileName: string;
}
```

**Example:**

```typescript
const action = createDeleteFileAction("old_notes.md");
// Deletes old_notes.md
```

### Advanced Actions

#### launch_intent

Launch an Android intent with parameters.

```typescript
interface LaunchIntentAction {
  type: 'launch_intent';
  intentName: string;
  parameters: Record<string, string>;
}
```


**Example:**

```typescript
const action = createLaunchIntentAction("VIEW_URL", { url: "https://example.com" });
// Opens URL in browser
```

#### done

Mark task as complete.

```typescript
interface DoneAction {
  type: 'done';
  success: boolean;
  text: string;
  filesToDisplay?: string[];
}
```

**Example:**

```typescript
const action = createDoneAction(true, "Successfully liked 3 posts", ["results.md"]);
// Completes task with success message and result file
```

### Utility Actions

#### take_screenshot

Capture a screenshot.

```typescript
interface TakeScreenshotAction {
  type: 'take_screenshot';
}
```

#### get_clipboard

Get clipboard content.

```typescript
interface GetClipboardAction {
  type: 'get_clipboard';
}
```

#### set_clipboard

Set clipboard content.

```typescript
interface SetClipboardAction {
  type: 'set_clipboard';
  text: string;
}
```

#### get_installed_apps

Get list of installed apps.

```typescript
interface GetInstalledAppsAction {
  type: 'get_installed_apps';
}
```

#### get_current_app

Get current app package name.

```typescript
interface GetCurrentAppAction {
  type: 'get_current_app';
}
```


### Dynamic Tool Actions

#### generate_tool

Generate a custom tool dynamically.

```typescript
interface GenerateToolAction {
  type: 'generate_tool';
  toolName: string;
  description: string;
  parameters: Record<string, any>;
}
```

**Example:**

```typescript
const action = createGenerateToolAction(
  "calculate_tip",
  "Calculate tip amount",
  { amount: "number", percentage: "number" }
);
```

#### execute_dynamic_tool

Execute a previously generated tool.

```typescript
interface ExecuteDynamicToolAction {
  type: 'execute_dynamic_tool';
  toolName: string;
  parameters: Record<string, any>;
}
```

**Example:**

```typescript
const action = createExecuteDynamicToolAction("calculate_tip", { amount: 50, percentage: 20 });
```

## Hooks

### useAgent

Main hook for executing AI-powered tasks.

```typescript
function useAgent(config: AgentConfig): UseAgentReturn
```

**Returns:**

```typescript
interface UseAgentReturn {
  execute: (task: string) => Promise<void>;
  isRunning: boolean;
  result: AgentResult | null;
  error: string | null;
  stop: () => void;
  history: AgentHistory[];
}
```

**Example:**

```typescript
const { execute, isRunning, result, error, stop, history } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  maxSteps: 100,
  debugMode: true,
  onStep: (step) => console.log(`Step ${step.stepNumber}`),
});

// Execute task
await execute("Open Instagram and like first post");

// Check result
if (result?.success) {
  console.log("Task completed!");
}

// Stop if needed
if (isRunning) {
  stop();
}
```


### useVoice

Hook for voice interactions (TTS and STT).

```typescript
function useVoice(): UseVoiceReturn
```

**Returns:**

```typescript
interface UseVoiceReturn {
  speak: (text: string) => Promise<void>;
  startListening: () => Promise<string>;
  stopListening: () => void;
  isListening: boolean;
  isSpeaking: boolean;
}
```

**Example:**

```typescript
const { speak, startListening, stopListening, isListening, isSpeaking } = useVoice();

// Text-to-speech
await speak("Hello, how can I help you?");

// Speech-to-text
const userInput = await startListening();
console.log("User said:", userInput);

// Stop listening
if (isListening) {
  stopListening();
}
```

### useFileSystem

Hook for file operations in sandboxed workspace.

```typescript
function useFileSystem(): UseFileSystemReturn
```

**Returns:**

```typescript
interface UseFileSystemReturn {
  writeFile: (name: string, content: string) => Promise<boolean>;
  readFile: (name: string) => Promise<string>;
  listFiles: () => Promise<string[]>;
}
```

**Example:**

```typescript
const { writeFile, readFile, listFiles } = useFileSystem();

// Write file
await writeFile("notes.md", "# My Notes\n\nContent here");

// Read file
const content = await readFile("notes.md");
console.log(content);

// List files
const files = await listFiles();
console.log("Files:", files);
```

## Components

### AgentProvider

Context provider for sharing agent configuration across components.

```typescript
interface AgentProviderProps {
  config: AgentConfig;
  children: React.ReactNode;
}
```

**Example:**

```typescript
<AgentProvider config={{ apiKey: 'YOUR_KEY', debugMode: true }}>
  <App />
</AgentProvider>
```


**Access context:**

```typescript
const { config, agent } = useAgentContext();
```

### AgentButton

Quick action button component for executing tasks.

```typescript
interface AgentButtonProps {
  task: string;
  onComplete?: (result: AgentResult) => void;
  onError?: (error: Error) => void;
  style?: ViewStyle;
  textStyle?: TextStyle;
}
```

**Example:**

```typescript
<AgentButton
  task="Open Settings"
  onComplete={(result) => console.log("Done:", result.message)}
  onError={(error) => console.error("Error:", error.message)}
  style={{ backgroundColor: 'blue' }}
/>
```

### DebugOverlay

Visual debug overlay showing agent state (only renders when `debugMode: true`).

```typescript
interface DebugOverlayProps {
  step: number;
  maxSteps: number;
  lastAction: string;
  elementCount: number;
  reasoning: string;
}
```

**Example:**

```typescript
<DebugOverlay
  step={5}
  maxSteps={100}
  lastAction="tap_element"
  elementCount={42}
  reasoning="Tapping on the search button to open search..."
/>
```

### DebugVisualFeedback

Component that provides visual feedback for debugging (element boxes, tap indicators).

```typescript
interface DebugVisualFeedbackProps {
  enabled: boolean;
  screenState?: ScreenState;
  lastAction?: Action;
}
```

**Example:**

```typescript
<DebugVisualFeedback
  enabled={debugMode}
  screenState={currentScreenState}
  lastAction={lastExecutedAction}
/>
```

### VoiceWaveAnimation

Animated wave visualization for voice input.

```typescript
<VoiceWaveAnimation isListening={isListening} />
```

### VoiceWaveAnimationAdvanced

Advanced wave animation with more visual effects.

```typescript
<VoiceWaveAnimationAdvanced isListening={isListening} isSpeaking={isSpeaking} />
```


## Error Handling

### Error Classes

#### QueError

Base error class for all QUE SDK errors.

```typescript
class QueError extends Error {
  constructor(
    message: string,
    public category: ErrorCategory,
    public recoverable: boolean,
    public details?: any
  )
}
```

**Example:**

```typescript
try {
  await execute("Open Settings");
} catch (error) {
  if (isQueError(error)) {
    console.log("Category:", error.category);
    console.log("Recoverable:", error.recoverable);
    console.log("Details:", error.details);
  }
}
```

#### NativeModuleError

Error from native Android module.

```typescript
class NativeModuleError extends QueError {
  constructor(message: string, details?: any)
}
```

**Common causes:**
- Accessibility Service not enabled
- Permission denied
- Native method failed

#### AccessibilityServiceError

Error specific to Accessibility Service.

```typescript
class AccessibilityServiceError extends NativeModuleError {
  constructor(message: string, details?: any)
}
```

**Common causes:**
- Service not bound
- Service not enabled in settings
- Service crashed

#### LLMError

Error from LLM communication.

```typescript
class LLMError extends QueError {
  constructor(message: string, details?: any)
}
```

**Common causes:**
- Invalid API key
- Rate limit exceeded
- Network timeout
- Model unavailable


#### LLMParseError

Error parsing LLM response.

```typescript
class LLMParseError extends LLMError {
  constructor(message: string, response: string)
}
```

**Common causes:**
- Invalid JSON in response
- Missing required fields
- Invalid action types

#### ActionExecutionError

Error executing an action.

```typescript
class ActionExecutionError extends QueError {
  constructor(message: string, action: Action, details?: any)
}
```

**Common causes:**
- Element not found
- Action failed (tap, type, etc.)
- Invalid parameters

#### ElementNotFoundError

Error when element ID doesn't exist.

```typescript
class ElementNotFoundError extends ActionExecutionError {
  constructor(elementId: number)
}
```

#### MaxStepsError

Error when max steps exceeded.

```typescript
class MaxStepsError extends QueError {
  constructor(steps: number)
}
```

#### MaxFailuresError

Error when max consecutive failures exceeded.

```typescript
class MaxFailuresError extends QueError {
  constructor(failures: number)
}
```

#### FileSystemError

Error from file operations.

```typescript
class FileSystemError extends QueError {
  constructor(message: string, fileName?: string, details?: any)
}
```

**Common causes:**
- Invalid file extension
- File not found
- Permission denied
- Disk full


#### VoiceError

Error from voice operations.

```typescript
class VoiceError extends QueError {
  constructor(message: string, details?: any)
}
```

**Common causes:**
- TTS not available
- STT not available
- Microphone permission denied
- Recognition failed

### Error Categories

```typescript
enum ErrorCategory {
  NATIVE_MODULE = 'native_module',
  ACCESSIBILITY_SERVICE = 'accessibility_service',
  LLM = 'llm',
  LLM_PARSE = 'llm_parse',
  ACTION_EXECUTION = 'action_execution',
  ELEMENT_NOT_FOUND = 'element_not_found',
  MAX_STEPS = 'max_steps',
  MAX_FAILURES = 'max_failures',
  FILE_SYSTEM = 'file_system',
  VOICE = 'voice',
  UNKNOWN = 'unknown',
}
```

### Error Helper Functions

#### isQueError

Check if error is a QueError.

```typescript
function isQueError(error: any): error is QueError
```

**Example:**

```typescript
if (isQueError(error)) {
  console.log("QUE Error:", error.category);
}
```

#### isRecoverableError

Check if error is recoverable.

```typescript
function isRecoverableError(error: Error): boolean
```

**Example:**

```typescript
if (isRecoverableError(error)) {
  console.log("Can retry this operation");
}
```

#### getErrorCategory

Get error category from any error.

```typescript
function getErrorCategory(error: Error): ErrorCategory
```

#### toQueError

Convert any error to QueError.

```typescript
function toQueError(error: any): QueError
```


#### formatErrorForLLM

Format error message for LLM context.

```typescript
function formatErrorForLLM(error: Error): string
```

**Example:**

```typescript
const errorMessage = formatErrorForLLM(error);
// "Action failed: Element 5 not found. The element may have disappeared or the screen changed."
```

#### createRecoveryMessage

Create recovery message for LLM after error.

```typescript
function createRecoveryMessage(error: Error, context?: string): string
```

**Example:**

```typescript
const recoveryMsg = createRecoveryMessage(error, "Trying to tap search button");
// "Previous action failed: Element not found. Please analyze the current screen and try a different approach."
```

## Native Module

### AccessibilityModule

Direct access to native Android Accessibility Service methods.

```typescript
interface AccessibilityModule {
  dumpHierarchy(): Promise<string>;
  clickOnPoint(x: number, y: number): Promise<boolean>;
  longPressOnPoint(x: number, y: number): Promise<boolean>;
  typeText(text: string): Promise<boolean>;
  scroll(direction: 'up' | 'down', amount: number): Promise<boolean>;
  performBack(): Promise<boolean>;
  performHome(): Promise<boolean>;
  performRecents(): Promise<boolean>;
  pressEnter(): Promise<boolean>;
  isKeyboardOpen(): Promise<boolean>;
  getCurrentActivity(): Promise<string>;
  openApp(packageName: string): Promise<boolean>;
  getScreenDimensions(): Promise<{ width: number; height: number }>;
  getScrollInfo(): Promise<{ pixelsAbove: number; pixelsBelow: number }>;
}
```

**Example:**

```typescript
import { accessibilityModule } from 'que-mobile-sdk';

// Get screen dimensions
const { width, height } = await accessibilityModule.getScreenDimensions();

// Tap at coordinates
await accessibilityModule.clickOnPoint(100, 200);

// Get UI hierarchy
const xml = await accessibilityModule.dumpHierarchy();
```

**Note:** Most users should use the Agent and hooks instead of calling native methods directly.


## Advanced Classes

### Agent

Core agent class that runs the SENSE → THINK → ACT loop.

```typescript
class Agent {
  constructor(config: AgentConfig)
  async run(task: string, maxSteps?: number): Promise<AgentResult>
  stop(): void
  getState(): AgentState
  getHistory(): AgentHistory[]
}
```

**Example:**

```typescript
import { Agent } from 'que-mobile-sdk';

const agent = new Agent({
  apiKey: process.env.GEMINI_API_KEY!,
  maxSteps: 50,
});

const result = await agent.run("Open Settings");
console.log(result);
```

### Perception

Screen analysis system that parses UI hierarchy.

```typescript
class Perception {
  constructor(nativeModule: AccessibilityModule, parser: SemanticParser)
  async analyze(previousState?: Set<string>): Promise<ScreenState>
}
```

### SemanticParser

XML parser for UI hierarchy.

```typescript
class SemanticParser {
  parse(xml: string): Element[]
  buildUIRepresentation(elements: Element[]): string
}
```

### GeminiClient

LLM client for Gemini API.

```typescript
class GeminiClient {
  constructor(config: GeminiClientConfig)
  async generateAgentOutput(messages: LLMMessage[]): Promise<AgentOutput | null>
}
```

### ActionExecutor

Executes actions via native module.

```typescript
class ActionExecutor {
  constructor(
    nativeModule: AccessibilityModule,
    fileSystem: FileSystem,
    voiceManager: VoiceManager
  )
  async execute(action: Action, screenState: ScreenState): Promise<ActionResult>
}
```

### FileSystem

Manages sandboxed file storage.

```typescript
class FileSystem {
  constructor(workspaceDir: string)
  async initialize(): Promise<void>
  async writeFile(fileName: string, content: string): Promise<boolean>
  async appendFile(fileName: string, content: string): Promise<boolean>
  async readFile(fileName: string): Promise<string>
  async listFiles(): Promise<string[]>
  async archiveOldFiles(): Promise<void>
}
```


### VoiceManager

Manages text-to-speech and speech-to-text.

```typescript
class VoiceManager {
  async speak(text: string): Promise<void>
  async ask(question: string): Promise<string>
  async startListening(): Promise<string>
  stopListening(): void
  isAvailable(): boolean
}
```

### MemoryManager

Manages conversation history and prompts.

```typescript
class MemoryManager {
  constructor(fileSystem: FileSystem, settings: AgentSettings)
  addNewTask(task: string): void
  createStateMessage(
    modelOutput: AgentOutput | null,
    result: ActionResult[] | null,
    stepInfo: { stepNumber: number; maxSteps: number },
    screenState: ScreenState
  ): void
  addContextMessage(message: string): void
  getMessages(): LLMMessage[]
  getAgentHistory(): HistoryItem[]
}
```

### PromptBuilder

Builds prompts for LLM.

```typescript
class PromptBuilder {
  constructor(config?: PromptBuilderConfig)
  buildSystemPrompt(): Promise<string>
  buildUserMessage(
    task: string,
    screenState: ScreenState,
    history: HistoryItem[],
    stepInfo: { stepNumber: number; maxSteps: number }
  ): string
}
```

## Type Guards

Type guard functions for action types:

```typescript
function isTapElementAction(action: Action): action is TapElementAction
function isLongPressElementAction(action: Action): action is LongPressElementAction
function isTapElementInputTextAndEnterAction(action: Action): action is TapElementInputTextAndEnterAction
function isTypeAction(action: Action): action is TypeAction
function isSwipeDownAction(action: Action): action is SwipeDownAction
function isSwipeUpAction(action: Action): action is SwipeUpAction
function isBackAction(action: Action): action is BackAction
function isHomeAction(action: Action): action is HomeAction
function isSwitchAppAction(action: Action): action is SwitchAppAction
function isWaitAction(action: Action): action is WaitAction
function isOpenAppAction(action: Action): action is OpenAppAction
function isSearchGoogleAction(action: Action): action is SearchGoogleAction
function isSpeakAction(action: Action): action is SpeakAction
function isAskAction(action: Action): action is AskAction
function isWriteFileAction(action: Action): action is WriteFileAction
function isAppendFileAction(action: Action): action is AppendFileAction
function isReadFileAction(action: Action): action is ReadFileAction
function isLaunchIntentAction(action: Action): action is LaunchIntentAction
function isDoneAction(action: Action): action is DoneAction
```

**Example:**

```typescript
if (isTapElementAction(action)) {
  console.log("Tapping element:", action.elementId);
} else if (isTypeAction(action)) {
  console.log("Typing:", action.text);
}
```

---

For more information, see:
- [README](../README.md)
- [Usage Guides](./GUIDES.md)
- [Debug Mode](./DEBUG_MODE.md)
- [Expo Plugin](./EXPO_PLUGIN.md)

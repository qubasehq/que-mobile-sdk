# QUE Mobile SDK

AI-powered Android automation framework for React Native/Expo with SENSE → THINK → ACT loop.

[![npm version](https://badge.fury.io/js/que-mobile-sdk.svg)](https://www.npmjs.com/package/que-mobile-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Overview

QUE Mobile SDK enables AI-powered mobile app control through natural language. The system captures screen state via Android Accessibility Services, processes it through an LLM (Gemini), and executes actions programmatically.

### Key Features

- 🤖 **AI-Powered Automation**: Control Android apps with natural language commands
- 🔄 **SENSE → THINK → ACT Loop**: Autonomous multi-step task execution
- ⚛️ **React Native/Expo**: Clean TypeScript API with React hooks
- 🎯 **19+ Action Types**: Tap, type, swipe, voice, file operations, and more
- 🗣️ **Voice Integration**: Built-in text-to-speech and speech-to-text
- 📁 **File System**: Sandboxed storage for task tracking and results
- ⏰ **Trigger System**: Scheduled and notification-based automated task execution
- 🐛 **Debug Mode**: Visual feedback and overlay for development
- 🔌 **Expo Plugin**: Zero-config setup for managed workflow

## Installation

```bash
npm install que-mobile-sdk
```

Or with yarn:

```bash
yarn add que-mobile-sdk
```

### Peer Dependencies

Ensure you have these installed:

```bash
npm install expo react react-native
```

## Quick Start

### 1. Configure Expo Plugin

Add the plugin to your `app.json`:

```json
{
  "expo": {
    "plugins": [
      "que-mobile-sdk"
    ]
  }
}
```

This automatically configures Android permissions and Accessibility Service.

### 2. Rebuild Native Code

```bash
npx expo prebuild --clean
npx expo run:android
```

### 3. Enable Accessibility Service

On your Android device:
1. Go to **Settings → Accessibility**
2. Find **QUE Accessibility Service**
3. Enable it

### 4. Use in Your App

```typescript
import { useAgent } from 'que-mobile-sdk';

function App() {
  const { execute, isRunning, result } = useAgent({
    apiKey: 'YOUR_GEMINI_API_KEY',
    maxSteps: 100,
  });

  const handleTask = async () => {
    await execute("Open Instagram and like the first post");
  };

  return (
    <View>
      <Button 
        title={isRunning ? "Running..." : "Execute Task"} 
        onPress={handleTask}
        disabled={isRunning}
      />
      {result && <Text>{result.message}</Text>}
    </View>
  );
}
```

## API Reference

### Hooks

#### `useAgent(config: AgentConfig)`

Main hook for executing AI-powered tasks.

**Parameters:**
- `config.apiKey` (string, required): Gemini API key
- `config.maxSteps` (number, optional): Maximum steps per task (default: 100)
- `config.maxFailures` (number, optional): Maximum consecutive failures (default: 3)
- `config.debugMode` (boolean, optional): Enable debug visualization (default: false)
- `config.onStep` (function, optional): Callback for each step
- `config.onComplete` (function, optional): Callback on task completion

**Returns:**
- `execute(task: string)`: Execute a task
- `isRunning` (boolean): Whether agent is currently running
- `result` (AgentResult | null): Task result
- `error` (string | null): Error message if failed
- `stop()`: Stop execution
- `history` (AgentHistory[]): Execution history

**Example:**
```typescript
const { execute, isRunning, result, error, stop } = useAgent({
  apiKey: process.env.GEMINI_API_KEY,
  maxSteps: 50,
  debugMode: true,
  onStep: (step) => console.log(`Step ${step.stepNumber}:`, step.action),
});

// Execute task
await execute("Search for 'React Native' on Google");

// Stop if needed
if (isRunning) {
  stop();
}
```

#### `useVoice()`

Hook for voice interactions.

**Returns:**
- `speak(text: string)`: Convert text to speech
- `startListening()`: Start speech recognition
- `stopListening()`: Stop speech recognition
- `isListening` (boolean): Whether currently listening
- `isSpeaking` (boolean): Whether currently speaking

**Example:**
```typescript
const { speak, startListening, isListening } = useVoice();

// Speak
await speak("Hello, how can I help you?");

// Listen
const userInput = await startListening();
console.log("User said:", userInput);
```

#### `useFileSystem()`

Hook for file operations.

**Returns:**
- `writeFile(name: string, content: string)`: Write file
- `readFile(name: string)`: Read file
- `listFiles()`: List all files

**Example:**
```typescript
const { writeFile, readFile, listFiles } = useFileSystem();

// Write
await writeFile("notes.md", "# My Notes\n\nTask completed!");

// Read
const content = await readFile("notes.md");

// List
const files = await listFiles();
```

### Components

#### `<AgentProvider>`

Context provider for sharing agent configuration.

```typescript
<AgentProvider config={{ apiKey: 'YOUR_KEY' }}>
  <App />
</AgentProvider>
```

#### `<AgentButton>`

Quick action button component.

```typescript
<AgentButton 
  task="Open Settings" 
  onComplete={(result) => console.log(result)}
/>
```

#### `<DebugOverlay>`

Visual debug overlay (only renders when `debugMode: true`).

```typescript
<DebugOverlay 
  step={5}
  maxSteps={100}
  lastAction="tap_element"
  elementCount={42}
  reasoning="Tapping on the search button..."
/>
```

### Action Types

The SDK supports 19+ action types:

| Action | Description | Example |
|--------|-------------|---------|
| `tap_element` | Tap on UI element | `{ type: 'tap_element', elementId: 5 }` |
| `long_press_element` | Long press element | `{ type: 'long_press_element', elementId: 5 }` |
| `type` | Type text | `{ type: 'type', text: 'Hello' }` |
| `swipe_down` | Swipe down | `{ type: 'swipe_down', amount: 500 }` |
| `swipe_up` | Swipe up | `{ type: 'swipe_up', amount: 500 }` |
| `back` | Press back button | `{ type: 'back' }` |
| `home` | Press home button | `{ type: 'home' }` |
| `switch_app` | Open app switcher | `{ type: 'switch_app' }` |
| `wait` | Wait 1 second | `{ type: 'wait' }` |
| `open_app` | Open app by name | `{ type: 'open_app', appName: 'Instagram' }` |
| `search_google` | Search on Google | `{ type: 'search_google', query: 'React Native' }` |
| `speak` | Text-to-speech | `{ type: 'speak', message: 'Hello' }` |
| `ask` | Ask user via voice | `{ type: 'ask', question: 'What is your name?' }` |
| `write_file` | Write file | `{ type: 'write_file', fileName: 'notes.md', content: '...' }` |
| `append_file` | Append to file | `{ type: 'append_file', fileName: 'notes.md', content: '...' }` |
| `read_file` | Read file | `{ type: 'read_file', fileName: 'notes.md' }` |
| `done` | Complete task | `{ type: 'done', success: true, text: 'Task completed!' }` |

See [API Documentation](./docs/API.md) for complete action reference.

## Configuration Options

### AgentConfig

```typescript
interface AgentConfig {
  // Required
  apiKey: string;                    // Gemini API key
  
  // Optional
  maxSteps?: number;                 // Max steps per task (default: 100)
  maxFailures?: number;              // Max consecutive failures (default: 3)
  debugMode?: boolean;               // Enable debug mode (default: false)
  model?: string;                    // Gemini model (default: 'gemini-2.0-flash-exp')
  
  // Callbacks
  onStep?: (step: AgentStep) => void;
  onComplete?: (result: AgentResult) => void;
  onError?: (error: Error) => void;
}
```

### Debug Mode

Enable visual feedback during development:

```typescript
const { execute } = useAgent({
  apiKey: 'YOUR_KEY',
  debugMode: true,  // Enable debug mode
});
```

Debug mode provides:
- 🟢 Green boxes around clickable elements
- 🟡 Yellow boxes around text elements
- 🔴 Red circles at tap locations
- ⚡ White border flash on screen changes
- 📊 Debug overlay with step info

See [Debug Mode Guide](./docs/DEBUG_MODE.md) for details.

## Usage Guides

### Basic Task Automation

```typescript
import { useAgent } from 'que-mobile-sdk';

function SimpleTask() {
  const { execute, isRunning, result } = useAgent({
    apiKey: process.env.GEMINI_API_KEY,
  });

  return (
    <Button
      title="Open Settings"
      onPress={() => execute("Open Settings app")}
      disabled={isRunning}
    />
  );
}
```

### Voice Integration

```typescript
import { useAgent, useVoice } from 'que-mobile-sdk';

function VoiceCommand() {
  const { execute } = useAgent({ apiKey: 'YOUR_KEY' });
  const { startListening, speak } = useVoice();

  const handleVoiceCommand = async () => {
    await speak("What would you like me to do?");
    const command = await startListening();
    await execute(command);
  };

  return (
    <Button title="Voice Command" onPress={handleVoiceCommand} />
  );
}
```

### File Operations

```typescript
import { useAgent } from 'que-mobile-sdk';

function FileTask() {
  const { execute } = useAgent({ apiKey: 'YOUR_KEY' });

  const task = `
    1. Create a file called shopping.md
    2. Write "Milk, Eggs, Bread" to it
    3. Read the file back to confirm
  `;

  return (
    <Button title="File Task" onPress={() => execute(task)} />
  );
}
```

### Multi-Step Tasks

```typescript
const task = `
  1. Open Instagram
  2. Search for "reactnative"
  3. Like the first 3 posts
  4. Return to home screen
`;

await execute(task);
```

### Automated Triggers

```typescript
import { useTriggers, TriggerManager } from 'que-mobile-sdk';

function ScheduledTask() {
  const { createTrigger } = useTriggers();

  const handleCreateTrigger = async () => {
    await createTrigger({
      id: TriggerManager.generateTriggerId(),
      type: 'schedule',
      enabled: true,
      priority: 10,
      task: 'Open Calendar and speak today\'s events',
      agentConfig: { apiKey: 'YOUR_KEY', maxSteps: 50 },
      schedule: {
        time: '08:00',
        daysOfWeek: [1, 2, 3, 4, 5], // Weekdays
      },
    });
  };

  return (
    <Button title="Schedule Morning Briefing" onPress={handleCreateTrigger} />
  );
}
```

See [Usage Guides](./docs/GUIDES.md) and [Trigger System Guide](./docs/TRIGGER_SYSTEM.md) for more examples.

## Troubleshooting

### Accessibility Service Not Enabled

**Problem**: Agent fails with "Accessibility Service not enabled"

**Solution**:
1. Go to **Settings → Accessibility**
2. Find **QUE Accessibility Service**
3. Enable it
4. Restart your app

### API Key Invalid

**Problem**: LLM errors with "Invalid API key"

**Solution**:
- Get a Gemini API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
- Ensure the key is correctly set in `AgentConfig`
- Don't commit API keys to version control

### Actions Not Executing

**Problem**: Agent sees elements but actions fail

**Solution**:
- Enable debug mode to visualize what the agent sees
- Check that Accessibility Service has all required permissions
- Ensure UI elements are actually clickable (not disabled)
- Try increasing wait times between actions

### Max Steps Exceeded

**Problem**: Task stops with "Max steps exceeded"

**Solution**:
- Increase `maxSteps` in config
- Break complex tasks into smaller subtasks
- Check if the task is actually achievable

### Element Not Found

**Problem**: Agent reports "Element not found"

**Solution**:
- Enable debug mode to see element IDs
- Ensure the screen has loaded completely
- Add wait actions before interacting with elements
- Check if the element is scrolled off-screen

### Performance Issues

**Problem**: Agent is slow or times out

**Solution**:
- Reduce `maxSteps` for simpler tasks
- Use faster Gemini model if available
- Ensure good network connection
- Close other apps to free memory

### Debug Mode Not Working

**Problem**: Debug overlay not showing

**Solution**:
- Ensure `debugMode: true` in config
- Check that `<DebugOverlay>` component is rendered
- Verify Accessibility Service is enabled
- Restart the app

## Examples

Check out the [example app](./example) for complete working examples:

- **Simple Task**: Basic task execution
- **Voice Command**: Voice-controlled automation
- **File Operations**: File system integration
- **Debug Mode**: Visual debugging
- **Multi-Step**: Complex multi-step tasks

Run the example:

```bash
cd example
npm install
npx expo prebuild
npx expo run:android
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│         React Native Application Layer          │
│  (useAgent, useVoice, useFileSystem hooks)      │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│           TypeScript Core Layer                  │
│  Agent, MemoryManager, Perception, LLM Client   │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│         Native Bridge Layer (Kotlin)             │
│     AccessibilityModule (NativeModule)          │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│      Android Accessibility Service               │
│   (UI Hierarchy, Touch, Gestures, System)       │
└─────────────────────────────────────────────────┘
```

### SENSE → THINK → ACT Loop

1. **SENSE**: Capture screen state via Accessibility Service
2. **THINK**: Process with Gemini LLM to decide next action
3. **ACT**: Execute action and observe result
4. Repeat until task complete or max steps reached

## Requirements

- **Platform**: Android only (iOS support planned)
- **React Native**: >= 0.72.0
- **Expo**: >= 50.0.0
- **Android**: >= API 24 (Android 7.0)
- **Node**: >= 18.0.0

## Contributing

Contributions are welcome! Please read our [Contributing Guide](./CONTRIBUTING.md) for details.

## License

MIT © [Sarath Babu](https://github.com/loyality7)

## Links

- [GitHub Repository](https://github.com/qubasehq/que-mobile-sdk)
- [npm Package](https://www.npmjs.com/package/que-mobile-sdk)
- [Issue Tracker](https://github.com/qubasehq/que-mobile-sdk/issues)
- [API Documentation](./docs/API.md)
- [Usage Guides](./docs/GUIDES.md)
- [Trigger System Guide](./docs/TRIGGER_SYSTEM.md)
- [Expo Plugin Guide](./docs/EXPO_PLUGIN.md)
- [Debug Mode Guide](./docs/DEBUG_MODE.md)

## Support

- 📧 Email: sarathyadav112@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/qubasehq/que-mobile-sdk/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/qubasehq/que-mobile-sdk/discussions)

---

Made with ❤️ by the QUE team

# QUE Mobile SDK Example App

This is a comprehensive example application demonstrating the capabilities of the QUE Mobile SDK - an AI-powered Android automation framework for React Native/Expo.

## Features Demonstrated

### 1. 🎯 Simple Task Execution
Execute basic automation tasks using natural language commands. Perfect for getting started with the SDK.

### 2. 🎤 Voice Commands
Control your device using voice commands with integrated speech-to-text and text-to-speech. Features animated voice waves during interaction.

### 3. 📁 File Operations
Demonstrate file system operations using both direct API calls and agent-based automation.

### 4. 🐛 Debug Mode
See real-time debug information and visual feedback during task execution with the debug overlay.

### 5. 🔄 Multi-Step Tasks
Execute complex, multi-step automation tasks that require navigation across different apps and screens.

## Getting Started

### Prerequisites

1. **Node.js** (v16 or higher)
2. **Expo CLI** (`npm install -g expo-cli`)
3. **Android device or emulator** (iOS not yet supported)
4. **Gemini API Key** from [Google AI Studio](https://makersuite.google.com/app/apikey)

### Installation

1. Navigate to the example directory:
```bash
cd que-mobile-sdk/example
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm start
```

4. Run on Android:
```bash
npm run android
```

### Configuration

#### Enable Accessibility Service

The QUE Mobile SDK requires accessibility permissions to interact with your device:

1. Go to **Settings** → **Accessibility**
2. Find **QUE Mobile SDK Example** in the list
3. Enable the accessibility service

#### Add Your API Key

You can add your Gemini API key in two ways:

1. **In each example screen** - Enter your API key in the provided input field
2. **Set as default** - Replace `'YOUR_GEMINI_API_KEY'` in the screen files with your actual key

## Project Structure

```
example/
├── App.tsx                          # Main app with navigation
├── screens/
│   ├── HomeScreen.tsx              # Landing page with example list
│   ├── SimpleTaskScreen.tsx        # Basic task execution
│   ├── VoiceCommandScreen.tsx      # Voice control with animations
│   ├── FileOperationsScreen.tsx    # File system operations
│   ├── DebugModeScreen.tsx         # Debug mode demonstration
│   └── MultiStepScreen.tsx         # Complex multi-step tasks
├── app.json                         # Expo configuration
├── package.json                     # Dependencies
└── tsconfig.json                    # TypeScript configuration
```

## Usage Examples

### Simple Task
```typescript
const { execute, isRunning, result } = useAgent({
  apiKey: 'YOUR_API_KEY',
  maxSteps: 20,
});

await execute('Open Settings');
```

### Voice Command
```typescript
const { speak, startListening } = useVoice();

// Listen for voice input
const transcript = await startListening();

// Speak response
await speak('Task completed successfully');
```

### File Operations
```typescript
const { readFile, writeFile, listFiles } = useFileSystem();

// Write a file
await writeFile('notes.txt', 'Hello World');

// Read a file
const content = await readFile('notes.txt');

// List all files
const files = await listFiles();
```

## Voice Wave Animation

The Voice Command screen features an animated wave visualization (similar to Blurr) that activates during:
- 🎤 **Listening** - High amplitude waves when recording voice
- 🔊 **Speaking** - Medium amplitude waves during text-to-speech
- ⚙️ **Processing** - Low amplitude waves during task execution

The animation uses multiple colorful wave layers with smooth transitions and real-time amplitude adjustments.

## Troubleshooting

### Accessibility Service Not Working
- Make sure you've enabled the accessibility service in Settings
- Try restarting the app after enabling permissions
- Check that the service is still enabled (some devices disable it automatically)

### API Key Issues
- Verify your API key is correct
- Check that you have API quota remaining
- Ensure you're using a Gemini API key (not other Google APIs)

### Build Errors
- Clear cache: `expo start -c`
- Reinstall dependencies: `rm -rf node_modules && npm install`
- Rebuild: `expo prebuild --clean`

## Learn More

- [QUE Mobile SDK Documentation](../README.md)
- [Expo Documentation](https://docs.expo.dev/)
- [React Navigation](https://reactnavigation.org/)
- [Gemini API](https://ai.google.dev/)

## License

MIT

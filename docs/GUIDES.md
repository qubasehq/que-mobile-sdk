# QUE Mobile SDK - Usage Guides

Comprehensive guides for common use cases and integration patterns.

## Table of Contents

- [Basic Task Automation](#basic-task-automation)
- [Voice Integration](#voice-integration)
- [File Operations](#file-operations)
- [Custom Actions](#custom-actions)
- [Expo Setup](#expo-setup)
- [Advanced Patterns](#advanced-patterns)

## Basic Task Automation

### Simple Task Execution

Execute a single task with the `useAgent` hook.

```typescript
import React from 'react';
import { View, Button, Text } from 'react-native';
import { useAgent } from 'que-mobile-sdk';

function SimpleTaskScreen() {
  const { execute, isRunning, result, error } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 50,
  });

  const handleOpenSettings = async () => {
    await execute("Open Settings app");
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title={isRunning ? "Running..." : "Open Settings"}
        onPress={handleOpenSettings}
        disabled={isRunning}
      />
      
      {result && (
        <Text style={{ marginTop: 20, color: result.success ? 'green' : 'red' }}>
          {result.message}
        </Text>
      )}
      
      {error && (
        <Text style={{ marginTop: 20, color: 'red' }}>
          Error: {error}
        </Text>
      )}
    </View>
  );
}

export default SimpleTaskScreen;
```

### Multi-Step Task

Execute complex tasks that require multiple steps.

```typescript
function MultiStepTaskScreen() {
  const { execute, isRunning, result } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 100,
    debugMode: true, // Enable to see what's happening
  });

  const handleInstagramTask = async () => {
    const task = `
      1. Open Instagram app
      2. Search for "reactnative"
      3. Like the first 3 posts
      4. Return to home screen
      5. Report completion
    `;
    
    await execute(task);
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title="Instagram Task"
        onPress={handleInstagramTask}
        disabled={isRunning}
      />
      
      {isRunning && (
        <Text style={{ marginTop: 20 }}>
          Executing task... This may take a minute.
        </Text>
      )}
      
      {result && (
        <View style={{ marginTop: 20 }}>
          <Text>Status: {result.success ? '✅ Success' : '❌ Failed'}</Text>
          <Text>Steps: {result.steps}</Text>
          <Text>Message: {result.message}</Text>
        </View>
      )}
    </View>
  );
}
```


### Task with Callbacks

Monitor task progress with callbacks.

```typescript
function TaskWithCallbacksScreen() {
  const [logs, setLogs] = React.useState<string[]>([]);

  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    onStep: (step) => {
      setLogs(prev => [...prev, `Step ${step.stepNumber}: ${step.action}`]);
    },
    onComplete: (result) => {
      setLogs(prev => [...prev, `✅ Completed: ${result.message}`]);
    },
    onError: (error) => {
      setLogs(prev => [...prev, `❌ Error: ${error.message}`]);
    },
  });

  return (
    <View style={{ padding: 20 }}>
      <Button
        title="Execute with Logs"
        onPress={() => execute("Open Chrome and search for React Native")}
        disabled={isRunning}
      />
      
      <ScrollView style={{ marginTop: 20, maxHeight: 400 }}>
        {logs.map((log, index) => (
          <Text key={index} style={{ fontSize: 12, marginBottom: 5 }}>
            {log}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}
```

### Using AgentButton Component

Quick action button for simple tasks.

```typescript
import { AgentButton } from 'que-mobile-sdk';

function QuickActionsScreen() {
  return (
    <View style={{ padding: 20 }}>
      <AgentButton
        task="Open Settings"
        onComplete={(result) => console.log('Settings opened:', result)}
        style={{ marginBottom: 10 }}
      />
      
      <AgentButton
        task="Take a screenshot"
        onComplete={(result) => console.log('Screenshot taken:', result)}
        style={{ marginBottom: 10 }}
      />
      
      <AgentButton
        task="Open YouTube and search for React Native tutorials"
        onComplete={(result) => console.log('YouTube search done:', result)}
      />
    </View>
  );
}
```

### Using AgentProvider

Share configuration across multiple components.

```typescript
import { AgentProvider, useAgentContext } from 'que-mobile-sdk';

// App.tsx
function App() {
  return (
    <AgentProvider config={{ apiKey: process.env.GEMINI_API_KEY!, debugMode: true }}>
      <Navigation />
    </AgentProvider>
  );
}

// Any child component
function ChildComponent() {
  const { config, agent } = useAgentContext();
  
  const handleTask = async () => {
    if (agent) {
      await agent.run("Open Settings");
    }
  };
  
  return <Button title="Execute" onPress={handleTask} />;
}
```


## Voice Integration

### Basic Voice Command

Use voice to control the agent.

```typescript
import { useAgent, useVoice } from 'que-mobile-sdk';

function VoiceCommandScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });
  
  const { speak, startListening, isListening } = useVoice();

  const handleVoiceCommand = async () => {
    try {
      // Ask user for command
      await speak("What would you like me to do?");
      
      // Listen for command
      const command = await startListening();
      console.log("User said:", command);
      
      // Confirm command
      await speak(`Executing: ${command}`);
      
      // Execute command
      await execute(command);
      
      // Announce completion
      await speak("Task completed!");
    } catch (error) {
      await speak("Sorry, I couldn't understand that.");
    }
  };

  return (
    <View style={{ padding: 20, alignItems: 'center' }}>
      <Button
        title={isListening ? "Listening..." : "Voice Command"}
        onPress={handleVoiceCommand}
        disabled={isRunning || isListening}
      />
      
      {isListening && (

        <VoiceWaveAnimation isListening={true} />
      )}
    </View>
  );
}
```

### Voice-Activated Agent

Continuously listen for wake word and commands.

```typescript
function VoiceActivatedAgent() {
  const { execute } = useAgent({ apiKey: process.env.GEMINI_API_KEY! });
  const { speak, startListening } = useVoice();
  const [isActive, setIsActive] = React.useState(false);

  const listenLoop = async () => {
    while (isActive) {
      try {
        const command = await startListening();
        
        if (command.toLowerCase().includes("hey assistant")) {
          await speak("Yes, how can I help?");
          const task = await startListening();
          await execute(task);
          await speak("Done!");
        }
      } catch (error) {
        console.error("Voice error:", error);
      }
    }
  };

  React.useEffect(() => {
    if (isActive) {
      listenLoop();
    }
  }, [isActive]);

  return (
    <View style={{ padding: 20 }}>
      <Button
        title={isActive ? "Stop Listening" : "Start Voice Assistant"}
        onPress={() => setIsActive(!isActive)}
      />
    </View>
  );
}
```

### Voice with Visual Feedback

Combine voice with wave animations.

```typescript
import { VoiceWaveAnimationAdvanced } from 'que-mobile-sdk';

function VoiceWithFeedbackScreen() {
  const { speak, startListening, isListening, isSpeaking } = useVoice();
  const [transcript, setTranscript] = React.useState("");

  const handleVoiceInput = async () => {
    const result = await startListening();
    setTranscript(result);
    await speak(`You said: ${result}`);
  };

  return (
    <View style={{ padding: 20, alignItems: 'center' }}>
      <VoiceWaveAnimationAdvanced
        isListening={isListening}
        isSpeaking={isSpeaking}
      />
      
      <Button
        title="Start Voice Input"
        onPress={handleVoiceInput}
        disabled={isListening || isSpeaking}
      />
      
      {transcript && (
        <Text style={{ marginTop: 20 }}>Transcript: {transcript}</Text>
      )}
    </View>
  );
}
```

### Ask Action in Tasks

Use the `ask` action to get user input during task execution.

```typescript
function InteractiveTaskScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });

  const handleInteractiveTask = async () => {
    const task = `
      1. Ask the user "What app would you like to open?"
      2. Open the app they mentioned
      3. Ask "What would you like to do in this app?"
      4. Execute their request
      5. Report completion
    `;
    
    await execute(task);
  };

  return (
    <Button
      title="Interactive Task"
      onPress={handleInteractiveTask}
      disabled={isRunning}
    />
  );
}
```


## File Operations

### Basic File Operations

Use the `useFileSystem` hook for file management.

```typescript
import { useFileSystem } from 'que-mobile-sdk';

function FileOperationsScreen() {
  const { writeFile, readFile, listFiles } = useFileSystem();
  const [files, setFiles] = React.useState<string[]>([]);
  const [content, setContent] = React.useState("");

  const handleCreateFile = async () => {
    await writeFile("notes.md", "# My Notes\n\nThis is a test note.");
    alert("File created!");
  };

  const handleReadFile = async () => {
    const data = await readFile("notes.md");
    setContent(data);
  };

  const handleListFiles = async () => {
    const fileList = await listFiles();
    setFiles(fileList);
  };

  return (
    <View style={{ padding: 20 }}>
      <Button title="Create File" onPress={handleCreateFile} />
      <Button title="Read File" onPress={handleReadFile} />
      <Button title="List Files" onPress={handleListFiles} />
      
      {content && (
        <Text style={{ marginTop: 20 }}>Content: {content}</Text>
      )}
      
      {files.length > 0 && (
        <View style={{ marginTop: 20 }}>
          <Text>Files:</Text>
          {files.map(file => <Text key={file}>- {file}</Text>)}
        </View>
      )}
    </View>
  );
}
```

### File Actions in Tasks

Use file actions within agent tasks.

```typescript
function TaskWithFilesScreen() {
  const { execute, isRunning, result } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });

  const handleShoppingList = async () => {
    const task = `
      1. Create a file called shopping_list.md
      2. Write "# Shopping List" as the header
      3. Append "- Milk" to the file
      4. Append "- Eggs" to the file
      5. Append "- Bread" to the file
      6. Read the file back to confirm
      7. Report completion with the file content
    `;
    
    await execute(task);
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title="Create Shopping List"
        onPress={handleShoppingList}
        disabled={isRunning}
      />
      
      {result?.files && result.files.length > 0 && (
        <View style={{ marginTop: 20 }}>
          <Text>Files created:</Text>
          {result.files.map(file => (
            <Text key={file}>✅ {file}</Text>
          ))}
        </View>
      )}
    </View>
  );
}
```

### Task Tracking with Files

Use files to track task progress and results.

```typescript
function TaskTrackerScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });

  const handleTrackedTask = async () => {
    const task = `
      1. Create a file called task_log.md
      2. Write "# Task Execution Log" as header
      3. Open Instagram
      4. Append "- Opened Instagram at [timestamp]" to task_log.md
      5. Search for "reactnative"
      6. Append "- Searched for reactnative" to task_log.md
      7. Like the first post
      8. Append "- Liked first post" to task_log.md
      9. Read task_log.md and report completion
    `;
    
    await execute(task);
  };

  return (
    <Button
      title="Execute Tracked Task"
      onPress={handleTrackedTask}
      disabled={isRunning}
    />
  );
}
```


### Data Collection Tasks

Collect data from apps and save to files.

```typescript
function DataCollectionScreen() {
  const { execute, isRunning, result } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 150,
  });

  const handleCollectData = async () => {
    const task = `
      1. Create a file called instagram_data.md
      2. Write "# Instagram Data Collection" as header
      3. Open Instagram
      4. Search for "reactnative"
      5. For the first 5 posts, collect:
         - Username
         - Caption (first 50 characters)
         - Number of likes
      6. Append each post's data to instagram_data.md
      7. Read the file and report completion
    `;
    
    await execute(task);
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title="Collect Instagram Data"
        onPress={handleCollectData}
        disabled={isRunning}
      />
      
      {isRunning && (
        <Text style={{ marginTop: 20 }}>
          Collecting data... This may take a few minutes.
        </Text>
      )}
    </View>
  );
}
```

## Custom Actions

### Creating Custom Action Handlers

Extend the SDK with custom action types.

```typescript
import { ActionExecutor, Action, ActionResult } from 'que-mobile-sdk';

// Define custom action type
interface CustomCalculateAction {
  type: 'custom_calculate';
  operation: 'add' | 'subtract' | 'multiply' | 'divide';
  a: number;
  b: number;
}

// Extend Action type
type ExtendedAction = Action | CustomCalculateAction;

// Create custom executor
class CustomActionExecutor extends ActionExecutor {
  async execute(action: ExtendedAction, screenState: ScreenState): Promise<ActionResult> {
    if (action.type === 'custom_calculate') {
      return this.handleCalculate(action);
    }
    
    // Fallback to default executor
    return super.execute(action as Action, screenState);
  }

  private async handleCalculate(action: CustomCalculateAction): Promise<ActionResult> {
    let result: number;
    
    switch (action.operation) {
      case 'add':
        result = action.a + action.b;
        break;
      case 'subtract':
        result = action.a - action.b;
        break;
      case 'multiply':
        result = action.a * action.b;
        break;
      case 'divide':
        result = action.a / action.b;
        break;
    }
    
    return {
      longTermMemory: `Calculated ${action.a} ${action.operation} ${action.b} = ${result}`,
      success: true,
    };
  }
}
```

### Using Dynamic Tools

Generate and execute custom tools at runtime.

```typescript
function DynamicToolsScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });

  const handleDynamicTool = async () => {
    const task = `
      1. Generate a tool called "calculate_tip" that:
         - Takes parameters: amount (number), percentage (number)
         - Returns the tip amount
      2. Execute the tool with amount=50, percentage=20
      3. Report the result
    `;
    
    await execute(task);
  };

  return (
    <Button
      title="Use Dynamic Tool"
      onPress={handleDynamicTool}
      disabled={isRunning}
    />
  );
}
```


### Registering Custom Tools

Register tools that the agent can use.

```typescript
import { ToolRegistry } from 'que-mobile-sdk';

// Register a custom tool
ToolRegistry.register({
  name: 'send_email',
  description: 'Send an email to a recipient',
  parameters: {
    to: { type: 'string', required: true },
    subject: { type: 'string', required: true },
    body: { type: 'string', required: true },
  },
  execute: async (params) => {
    // Your email sending logic here
    console.log('Sending email:', params);
    return { success: true, message: 'Email sent!' };
  },
});

// Use in task
function CustomToolTaskScreen() {
  const { execute } = useAgent({ apiKey: process.env.GEMINI_API_KEY! });

  const handleSendEmail = async () => {
    await execute("Send an email to john@example.com with subject 'Hello' and body 'Test message'");
  };

  return <Button title="Send Email" onPress={handleSendEmail} />;
}
```

## Expo Setup

### Initial Setup

Step-by-step guide to set up QUE SDK in an Expo project.

#### 1. Create Expo Project

```bash
npx create-expo-app my-que-app
cd my-que-app
```

#### 2. Install QUE SDK

```bash
npm install que-mobile-sdk
```

#### 3. Configure Plugin

Edit `app.json`:

```json
{
  "expo": {
    "name": "My QUE App",
    "slug": "my-que-app",
    "plugins": [
      "que-mobile-sdk"
    ],
    "android": {
      "package": "com.mycompany.myqueapp"
    }
  }
}
```

#### 4. Add Environment Variables

Create `.env` file:

```
GEMINI_API_KEY=your_api_key_here
```

Install dotenv:

```bash
npm install react-native-dotenv
```

Configure babel.config.js:

```javascript
module.exports = function(api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      ['module:react-native-dotenv', {
        moduleName: '@env',
        path: '.env',
      }]
    ]
  };
};
```

#### 5. Prebuild Native Code

```bash
npx expo prebuild --clean
```

#### 6. Run on Android

```bash
npx expo run:android
```

#### 7. Enable Accessibility Service

On your Android device:
1. Go to **Settings → Accessibility**
2. Find **QUE Accessibility Service**
3. Enable it

### Basic App Structure

```typescript
// App.tsx
import React from 'react';
import { View, Button, Text } from 'react-native';
import { useAgent } from 'que-mobile-sdk';
import { GEMINI_API_KEY } from '@env';

export default function App() {
  const { execute, isRunning, result } = useAgent({
    apiKey: GEMINI_API_KEY,
    debugMode: __DEV__, // Enable debug mode in development
  });

  return (
    <View style={{ flex: 1, justifyContent: 'center', padding: 20 }}>
      <Text style={{ fontSize: 24, marginBottom: 20, textAlign: 'center' }}>
        QUE Mobile SDK Demo
      </Text>
      
      <Button
        title={isRunning ? "Running..." : "Open Settings"}
        onPress={() => execute("Open Settings app")}
        disabled={isRunning}
      />
      
      {result && (
        <Text style={{ marginTop: 20, textAlign: 'center' }}>
          {result.message}
        </Text>
      )}
    </View>
  );
}
```


### Navigation Setup

Integrate with React Navigation.

```bash
npm install @react-navigation/native @react-navigation/native-stack
npm install react-native-screens react-native-safe-area-context
```

```typescript
// App.tsx
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { AgentProvider } from 'que-mobile-sdk';
import { GEMINI_API_KEY } from '@env';

import HomeScreen from './screens/HomeScreen';
import TaskScreen from './screens/TaskScreen';

const Stack = createNativeStackNavigator();

export default function App() {
  return (
    <AgentProvider config={{ apiKey: GEMINI_API_KEY, debugMode: __DEV__ }}>
      <NavigationContainer>
        <Stack.Navigator>
          <Stack.Screen name="Home" component={HomeScreen} />
          <Stack.Screen name="Task" component={TaskScreen} />
        </Stack.Navigator>
      </NavigationContainer>
    </AgentProvider>
  );
}
```

### Production Build

Build for production release.

```bash
# Build APK
eas build --platform android --profile production

# Or local build
npx expo run:android --variant release
```

Update `app.json` for production:

```json
{
  "expo": {
    "android": {
      "permissions": [
        "BIND_ACCESSIBILITY_SERVICE",
        "INTERNET",
        "RECORD_AUDIO"
      ],
      "versionCode": 1,
      "package": "com.mycompany.myqueapp"
    }
  }
}
```

## Advanced Patterns

### Error Handling

Comprehensive error handling strategy.

```typescript
import { isQueError, ErrorCategory } from 'que-mobile-sdk';

function RobustTaskScreen() {
  const { execute, isRunning, error } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    maxFailures: 5, // Allow more retries
    onError: (error) => {
      if (isQueError(error)) {
        switch (error.category) {
          case ErrorCategory.ACCESSIBILITY_SERVICE:
            alert('Please enable Accessibility Service in Settings');
            break;
          case ErrorCategory.LLM:
            alert('API error. Check your API key and network connection.');
            break;
          case ErrorCategory.MAX_STEPS:
            alert('Task took too long. Try breaking it into smaller steps.');
            break;
          default:
            alert(`Error: ${error.message}`);
        }
      }
    },
  });

  const handleTaskWithRetry = async () => {
    try {
      await execute("Open Instagram and like first post");
    } catch (error) {
      console.error('Task failed:', error);
      
      // Retry with simpler task
      if (isQueError(error) && error.recoverable) {
        console.log('Retrying with simpler task...');
        await execute("Open Instagram");
      }
    }
  };

  return (
    <Button
      title="Execute with Error Handling"
      onPress={handleTaskWithRetry}
      disabled={isRunning}
    />
  );
}
```

### State Management

Integrate with Redux or other state management.

```typescript
import { useDispatch, useSelector } from 'react-redux';
import { useAgent } from 'que-mobile-sdk';

function ReduxIntegratedScreen() {
  const dispatch = useDispatch();
  const tasks = useSelector(state => state.tasks);

  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    onStep: (step) => {
      dispatch({ type: 'TASK_STEP', payload: step });
    },
    onComplete: (result) => {
      dispatch({ type: 'TASK_COMPLETE', payload: result });
    },
  });

  return (
    <View>
      {tasks.map(task => (
        <Button
          key={task.id}
          title={task.name}
          onPress={() => execute(task.description)}
          disabled={isRunning}
        />
      ))}
    </View>
  );
}
```


### Background Tasks

Execute tasks in the background (requires additional setup).

```typescript
import BackgroundFetch from 'react-native-background-fetch';
import { Agent } from 'que-mobile-sdk';

// Configure background task
BackgroundFetch.configure({
  minimumFetchInterval: 15, // minutes
}, async (taskId) => {
  console.log('[BackgroundFetch] Task started:', taskId);
  
  const agent = new Agent({
    apiKey: process.env.GEMINI_API_KEY!,
  });
  
  try {
    await agent.run("Check notifications and report any important ones");
    BackgroundFetch.finish(taskId);
  } catch (error) {
    console.error('[BackgroundFetch] Error:', error);
    BackgroundFetch.finish(taskId);
  }
}, (error) => {
  console.error('[BackgroundFetch] Failed to start:', error);
});
```

### Scheduled Tasks

Schedule tasks to run at specific times.

```typescript
import { useAgent } from 'que-mobile-sdk';
import { useEffect } from 'react';

function ScheduledTasksScreen() {
  const { execute } = useAgent({ apiKey: process.env.GEMINI_API_KEY! });

  useEffect(() => {
    // Schedule daily task at 9 AM
    const scheduleDailyTask = () => {
      const now = new Date();
      const scheduledTime = new Date();
      scheduledTime.setHours(9, 0, 0, 0);
      
      if (now > scheduledTime) {
        scheduledTime.setDate(scheduledTime.getDate() + 1);
      }
      
      const timeUntilTask = scheduledTime.getTime() - now.getTime();
      
      setTimeout(async () => {
        await execute("Check calendar and send summary of today's events");
        scheduleDailyTask(); // Reschedule for next day
      }, timeUntilTask);
    };
    
    scheduleDailyTask();
  }, []);

  return <Text>Scheduled tasks are running in background</Text>;
}
```

### Chaining Tasks

Execute multiple tasks in sequence.

```typescript
function ChainedTasksScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
  });

  const handleChainedTasks = async () => {
    // Task 1: Collect data
    await execute("Open Instagram, search for 'reactnative', and save first 5 usernames to users.md");
    
    // Task 2: Process data
    await execute("Read users.md and create a summary in summary.md");
    
    // Task 3: Share results
    await execute("Read summary.md and speak the contents");
  };

  return (
    <Button
      title="Execute Chained Tasks"
      onPress={handleChainedTasks}
      disabled={isRunning}
    />
  );
}
```

### Conditional Tasks

Execute tasks based on conditions.

```typescript
function ConditionalTaskScreen() {
  const { execute } = useAgent({ apiKey: process.env.GEMINI_API_KEY! });
  const [condition, setCondition] = React.useState<'morning' | 'evening'>('morning');

  const handleConditionalTask = async () => {
    if (condition === 'morning') {
      await execute(`
        1. Open Calendar
        2. Check today's events
        3. Create a file called today.md with the schedule
        4. Speak "Good morning! Here's your schedule for today"
        5. Read the schedule aloud
      `);
    } else {
      await execute(`
        1. Open Calendar
        2. Check tomorrow's events
        3. Create a file called tomorrow.md with the schedule
        4. Speak "Good evening! Here's your schedule for tomorrow"
        5. Read the schedule aloud
      `);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title="Morning Routine"
        onPress={() => {
          setCondition('morning');
          handleConditionalTask();
        }}
      />
      <Button
        title="Evening Routine"
        onPress={() => {
          setCondition('evening');
          handleConditionalTask();
        }}
      />
    </View>
  );
}
```

### Performance Optimization

Optimize agent performance for complex tasks.

```typescript
function OptimizedTaskScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 50, // Limit steps for faster execution
    model: 'gemini-2.0-flash-exp', // Use faster model
    debugMode: false, // Disable debug mode in production
  });

  // Memoize task to avoid recreating
  const optimizedTask = React.useMemo(() => `
    1. Open Settings
    2. Navigate to About Phone
    3. Report device model
  `, []);

  return (
    <Button
      title="Optimized Task"
      onPress={() => execute(optimizedTask)}
      disabled={isRunning}
    />
  );
}
```

---

## Best Practices

### 1. API Key Security

Never hardcode API keys:

```typescript
// ❌ Bad
const config = { apiKey: 'AIza...' };

// ✅ Good
import { GEMINI_API_KEY } from '@env';
const config = { apiKey: GEMINI_API_KEY };
```

### 2. Error Handling

Always handle errors gracefully:

```typescript
const { execute, error } = useAgent({ apiKey: API_KEY });

if (error) {
  // Show user-friendly error message
  alert('Task failed. Please try again.');
}
```

### 3. Task Clarity

Write clear, specific tasks:

```typescript
// ❌ Vague
await execute("Do something with Instagram");

// ✅ Clear
await execute("Open Instagram, search for 'reactnative', and like the first post");
```

### 4. Step Limits

Set appropriate step limits:

```typescript
// Simple task
const config = { apiKey: API_KEY, maxSteps: 20 };

// Complex task
const config = { apiKey: API_KEY, maxSteps: 150 };
```

### 5. Debug Mode

Use debug mode during development:

```typescript
const config = {
  apiKey: API_KEY,
  debugMode: __DEV__, // Only in development
};
```

---

For more information, see:
- [README](../README.md)
- [API Documentation](./API.md)
- [Debug Mode](./DEBUG_MODE.md)
- [Expo Plugin](./EXPO_PLUGIN.md)

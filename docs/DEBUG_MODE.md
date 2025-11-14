# Debug Mode

Debug mode provides visual feedback and detailed logging to help you understand what the agent sees and does during task execution.

## Enabling Debug Mode

Enable debug mode in your agent configuration:

```typescript
import { useAgent } from 'que-mobile-sdk';

const { execute, isRunning } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  debugMode: true, // Enable debug mode
});
```

Or use it only in development:

```typescript
const { execute } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  debugMode: __DEV__, // Only in development builds
});
```

## Visual Features

When debug mode is enabled, you'll see:

### 1. Element Bounding Boxes

- **Green boxes**: Clickable/interactive elements
- **Yellow boxes**: Text elements
- **Element IDs**: Numeric labels on each element

This helps you understand which elements the agent can interact with.

### 2. Tap Indicators

- **Red circles**: Show where the agent tapped
- Appears briefly at tap coordinates
- Helps verify tap accuracy

### 3. Screen Flash

- **White border flash**: Indicates screen state changed
- Flashes for 500ms when perception analyzes the screen
- Helps track when the agent is "sensing"

### 4. Debug Overlay

Shows real-time agent state:
- Current step number / max steps
- Last action executed
- Number of elements detected
- LLM reasoning (truncated)

## Using Debug Overlay Component

Add the debug overlay to your screen:

```typescript
import { DebugOverlay } from 'que-mobile-sdk';

function MyScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    debugMode: true,
  });

  const [debugInfo, setDebugInfo] = React.useState({
    step: 0,
    maxSteps: 100,
    lastAction: '',
    elementCount: 0,
    reasoning: '',
  });

  return (
    <View>
      {/* Your UI */}
      
      {/* Debug overlay - only renders when debugMode is true */}
      <DebugOverlay
        step={debugInfo.step}
        maxSteps={debugInfo.maxSteps}
        lastAction={debugInfo.lastAction}
        elementCount={debugInfo.elementCount}
        reasoning={debugInfo.reasoning}
      />
    </View>
  );
}
```


## Using Debug Visual Feedback

For more advanced visual debugging:

```typescript
import { DebugVisualFeedback } from 'que-mobile-sdk';

function MyScreen() {
  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    debugMode: true,
  });

  const [screenState, setScreenState] = React.useState(null);
  const [lastAction, setLastAction] = React.useState(null);

  return (
    <View>
      {/* Your UI */}
      
      {/* Visual feedback overlay */}
      <DebugVisualFeedback
        enabled={true}
        screenState={screenState}
        lastAction={lastAction}
      />
    </View>
  );
}
```

## Logging

Debug mode also enables detailed console logging:

```typescript
const { execute } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  debugMode: true,
  onStep: (step) => {
    console.log('=== STEP', step.stepNumber, '===');
    console.log('Action:', step.action);
    console.log('Screen elements:', step.elementCount);
    console.log('Reasoning:', step.reasoning);
  },
});
```

## Performance Impact

Debug mode has minimal performance impact:
- Visual overlays: ~5-10ms per frame
- Logging: ~1-2ms per step
- Element highlighting: ~10-20ms per screen update

For production builds, always disable debug mode:

```typescript
const { execute } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  debugMode: false, // Disable in production
});
```

## Debugging Common Issues

### Elements Not Visible

If you don't see element boxes:
1. Ensure `debugMode: true` is set
2. Check that Accessibility Service is enabled
3. Verify the screen has interactive elements
4. Try scrolling - some elements may be off-screen

### Tap Indicators Not Showing

If tap indicators don't appear:
1. Verify `debugMode: true`
2. Check that the action is actually executing
3. Look for errors in console logs
4. Ensure the element exists in the element map

### Overlay Not Rendering

If the debug overlay doesn't show:
1. Ensure you've added `<DebugOverlay>` component
2. Check that `debugMode: true` in config
3. Verify the component is not hidden by other UI
4. Check z-index/elevation of the overlay

### Screen Flash Too Bright

The white border flash can be adjusted by modifying the DebugVisualFeedback component styling.

## Best Practices

### 1. Use in Development Only

```typescript
const config = {
  apiKey: API_KEY,
  debugMode: __DEV__, // Automatically disabled in production
};
```

### 2. Combine with Callbacks

```typescript
const { execute } = useAgent({
  apiKey: API_KEY,
  debugMode: true,
  onStep: (step) => {
    // Log detailed step info
    console.log('Step:', step);
  },
  onError: (error) => {
    // Log errors with full details
    console.error('Error:', error);
  },
});
```

### 3. Save Debug Logs

```typescript
const [logs, setLogs] = React.useState<string[]>([]);

const { execute } = useAgent({
  apiKey: API_KEY,
  debugMode: true,
  onStep: (step) => {
    const logEntry = `[${new Date().toISOString()}] Step ${step.stepNumber}: ${step.action}`;
    setLogs(prev => [...prev, logEntry]);
  },
});

// Later, save logs to file
const saveLogs = async () => {
  await FileSystem.writeFile('debug_logs.txt', logs.join('\n'));
};
```

### 4. Conditional Debug Features

```typescript
const DEBUG_LEVEL = __DEV__ ? 'verbose' : 'none';

const { execute } = useAgent({
  apiKey: API_KEY,
  debugMode: DEBUG_LEVEL === 'verbose',
  onStep: DEBUG_LEVEL === 'verbose' ? (step) => console.log(step) : undefined,
});
```

## Debug Mode API

### AgentConfig.debugMode

```typescript
interface AgentConfig {
  debugMode?: boolean; // Default: false
}
```

When `true`:
- Enables visual feedback (boxes, indicators, flash)
- Enables detailed console logging
- Renders debug overlay components
- Tracks additional metrics

### DebugOverlay Props

```typescript
interface DebugOverlayProps {
  step: number;           // Current step number
  maxSteps: number;       // Maximum steps allowed
  lastAction: string;     // Last action type executed
  elementCount: number;   // Number of elements on screen
  reasoning: string;      // LLM reasoning (truncated)
}
```

### DebugVisualFeedback Props

```typescript
interface DebugVisualFeedbackProps {
  enabled: boolean;           // Whether to show visual feedback
  screenState?: ScreenState;  // Current screen state
  lastAction?: Action;        // Last action executed
}
```

## Examples

### Full Debug Setup

```typescript
import React from 'react';
import { View, ScrollView, Text } from 'react-native';
import { useAgent, DebugOverlay, DebugVisualFeedback } from 'que-mobile-sdk';

function DebugScreen() {
  const [logs, setLogs] = React.useState<string[]>([]);
  const [debugInfo, setDebugInfo] = React.useState({
    step: 0,
    maxSteps: 100,
    lastAction: '',
    elementCount: 0,
    reasoning: '',
  });

  const { execute, isRunning } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    debugMode: true,
    maxSteps: 100,
    onStep: (step) => {
      setLogs(prev => [...prev, `Step ${step.stepNumber}: ${step.action}`]);
      setDebugInfo({
        step: step.stepNumber,
        maxSteps: 100,
        lastAction: step.action,
        elementCount: step.elementCount || 0,
        reasoning: step.reasoning || '',
      });
    },
  });

  return (
    <View style={{ flex: 1 }}>
      {/* Main content */}
      <View style={{ padding: 20 }}>
        <Button
          title="Execute Task"
          onPress={() => execute("Open Settings")}
          disabled={isRunning}
        />
      </View>

      {/* Debug logs */}
      <ScrollView style={{ flex: 1, padding: 10, backgroundColor: '#000' }}>
        {logs.map((log, i) => (
          <Text key={i} style={{ color: '#0f0', fontFamily: 'monospace', fontSize: 10 }}>
            {log}
          </Text>
        ))}
      </ScrollView>

      {/* Debug overlays */}
      <DebugOverlay {...debugInfo} />
      <DebugVisualFeedback enabled={true} />
    </View>
  );
}
```

## Troubleshooting

### High Memory Usage

If debug mode causes memory issues:
1. Limit log history: `setLogs(prev => prev.slice(-100))`
2. Disable visual feedback if not needed
3. Use debug mode only for specific tasks

### Performance Degradation

If the app becomes slow:
1. Disable debug overlay: Don't render `<DebugOverlay>`
2. Reduce logging frequency
3. Use debug mode only when needed

### Visual Artifacts

If you see visual glitches:
1. Check z-index of debug components
2. Ensure proper cleanup on unmount
3. Verify React Native version compatibility

---

For more information, see:
- [README](../README.md)
- [API Documentation](./API.md)
- [Debug Visual Feedback](./DEBUG_VISUAL_FEEDBACK.md)
- [Usage Guides](./GUIDES.md)

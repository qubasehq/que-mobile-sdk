# Debug Visual Feedback

Visual debugging features that help you see what the agent sees and does in real-time.

## Overview

The `DebugVisualFeedback` component provides visual overlays that show:
- Element bounding boxes with IDs
- Tap location indicators
- Screen state change notifications
- Real-time interaction feedback

## Basic Usage

```typescript
import { DebugVisualFeedback } from 'que-mobile-sdk';

function MyScreen() {
  const [screenState, setScreenState] = React.useState(null);
  const [lastAction, setLastAction] = React.useState(null);

  return (
    <View style={{ flex: 1 }}>
      {/* Your app content */}
      
      {/* Debug visual feedback overlay */}
      <DebugVisualFeedback
        enabled={__DEV__}
        screenState={screenState}
        lastAction={lastAction}
      />
    </View>
  );
}
```

## Visual Features

### 1. Element Bounding Boxes

Shows colored boxes around UI elements:

**Green Boxes** - Clickable/Interactive Elements
- Buttons
- Links
- Tappable items
- Input fields (when not focused)

**Yellow Boxes** - Text Elements
- Labels
- Text views
- Non-interactive text

**Element IDs**
- Small numeric labels on each element
- Corresponds to element IDs in agent actions
- Helps identify which element the agent will interact with

### 2. Tap Indicators

**Red Circles** at tap locations:
- Appears when agent executes `tap_element` action
- Shows exact coordinates of tap
- Fades out after 1 second
- Helps verify tap accuracy

### 3. Screen Flash

**White Border Flash**:
- Flashes when screen state changes
- Duration: 500ms
- Indicates the agent is analyzing the screen
- Helps track SENSE phase of the loop

### 4. Long Press Indicators

**Blue Circles** for long press:
- Similar to tap indicators but blue
- Shows long press location
- Fades out after 1.5 seconds

## Component Props

```typescript
interface DebugVisualFeedbackProps {
  enabled: boolean;           // Master switch for all visual feedback
  screenState?: ScreenState;  // Current screen state with elements
  lastAction?: Action;        // Last action executed by agent
  showElementBoxes?: boolean; // Show element bounding boxes (default: true)
  showTapIndicators?: boolean; // Show tap indicators (default: true)
  showScreenFlash?: boolean;  // Show screen flash (default: true)
  boxOpacity?: number;        // Opacity of element boxes (default: 0.3)
  tapIndicatorSize?: number;  // Size of tap indicator (default: 50)
}
```

## Advanced Configuration

### Custom Styling

```typescript
<DebugVisualFeedback
  enabled={true}
  screenState={screenState}
  lastAction={lastAction}
  boxOpacity={0.5}           // More visible boxes
  tapIndicatorSize={80}      // Larger tap indicators
/>
```

### Selective Features

Enable only specific features:

```typescript
<DebugVisualFeedback
  enabled={true}
  screenState={screenState}
  showElementBoxes={true}    // Show boxes
  showTapIndicators={false}  // Hide tap indicators
  showScreenFlash={false}    // Hide screen flash
/>
```


### Conditional Rendering

Show visual feedback only in specific scenarios:

```typescript
function MyScreen() {
  const [showDebug, setShowDebug] = React.useState(__DEV__);
  
  return (
    <View>
      {/* Toggle button */}
      <Button
        title={showDebug ? "Hide Debug" : "Show Debug"}
        onPress={() => setShowDebug(!showDebug)}
      />
      
      {/* Conditional debug overlay */}
      <DebugVisualFeedback
        enabled={showDebug}
        screenState={screenState}
        lastAction={lastAction}
      />
    </View>
  );
}
```

## Integration with Agent

### Automatic Integration

When using `useAgent` with `debugMode: true`, visual feedback is automatically enabled:

```typescript
const { execute } = useAgent({
  apiKey: process.env.GEMINI_API_KEY!,
  debugMode: true, // Automatically enables DebugVisualFeedback
});
```

### Manual Integration

For more control, integrate manually:

```typescript
import { Agent, DebugVisualFeedback } from 'que-mobile-sdk';

function MyScreen() {
  const [screenState, setScreenState] = React.useState(null);
  const [lastAction, setLastAction] = React.useState(null);
  
  const agent = React.useMemo(() => new Agent({
    apiKey: process.env.GEMINI_API_KEY!,
    onStep: (step) => {
      setScreenState(step.screenState);
      setLastAction(step.lastAction);
    },
  }), []);

  return (
    <View>
      <Button
        title="Execute"
        onPress={() => agent.run("Open Settings")}
      />
      
      <DebugVisualFeedback
        enabled={true}
        screenState={screenState}
        lastAction={lastAction}
      />
    </View>
  );
}
```

## Understanding Element Boxes

### Box Colors

| Color | Meaning | Examples |
|-------|---------|----------|
| 🟢 Green | Clickable/Interactive | Buttons, links, checkboxes |
| 🟡 Yellow | Text/Display | Labels, text views, headers |

### Element IDs

Each box shows a numeric ID (e.g., "5", "12", "23"):
- These IDs correspond to `elementId` in actions
- Agent uses these IDs to target specific elements
- IDs are assigned sequentially during screen analysis

Example:
```typescript
// Agent sees element with ID 5
// Agent generates action: { type: 'tap_element', elementId: 5 }
// Visual feedback shows green box with "5" label
// Red circle appears at tap location
```

### Box Positioning

Boxes are positioned based on element bounds from Accessibility Service:
- `[left, top][right, bottom]` coordinates
- Automatically scaled to screen dimensions
- Updated on each screen state change

## Understanding Tap Indicators

### Tap Indicator Lifecycle

1. **Action Execution**: Agent executes `tap_element` action
2. **Indicator Appears**: Red circle appears at tap coordinates
3. **Fade Out**: Circle fades over 1 second
4. **Removal**: Indicator removed from view

### Tap Accuracy

Tap indicators help verify:
- Agent is tapping the correct element
- Tap coordinates are accurate
- Element center calculation is correct

If taps are inaccurate:
- Check element bounds in screen state
- Verify screen dimensions are correct
- Ensure no UI scaling issues

## Understanding Screen Flash

### Flash Timing

Screen flash occurs during the SENSE phase:
1. Agent calls `perception.analyze()`
2. White border appears
3. UI hierarchy is captured
4. Border fades after 500ms
5. Agent proceeds to THINK phase

### Flash Frequency

Flash frequency indicates agent activity:
- **Fast flashing**: Agent is actively working
- **Slow flashing**: Agent is waiting or processing
- **No flashing**: Agent is idle or stopped

## Performance Considerations

### Rendering Cost

Visual feedback has minimal performance impact:
- Element boxes: ~10-20ms per render
- Tap indicators: ~5ms per indicator
- Screen flash: ~2ms per flash

### Memory Usage

Memory usage is proportional to element count:
- ~1KB per element box
- ~500 bytes per tap indicator
- Negligible for screen flash

### Optimization Tips

1. **Disable when not needed**:
```typescript
<DebugVisualFeedback enabled={false} />
```

2. **Limit element boxes**:
```typescript
<DebugVisualFeedback
  enabled={true}
  showElementBoxes={false} // Disable boxes, keep indicators
/>
```

3. **Use in development only**:
```typescript
<DebugVisualFeedback enabled={__DEV__} />
```

## Troubleshooting

### Boxes Not Showing

**Problem**: Element boxes don't appear

**Solutions**:
1. Verify `enabled={true}`
2. Check that `screenState` is provided
3. Ensure Accessibility Service is enabled
4. Verify elements exist in screen state

### Boxes in Wrong Position

**Problem**: Boxes don't align with actual elements

**Solutions**:
1. Check screen dimensions match device
2. Verify no UI scaling or zoom
3. Ensure bounds are in correct coordinate system
4. Check for status bar/navigation bar offsets

### Tap Indicators Not Appearing

**Problem**: Red circles don't show on tap

**Solutions**:
1. Verify `lastAction` is provided
2. Check that action type is `tap_element`
3. Ensure `showTapIndicators={true}`
4. Verify tap coordinates are valid

### Screen Flash Too Bright

**Problem**: White flash is too intense

**Solutions**:
1. Adjust flash opacity in component
2. Use custom flash color
3. Disable flash: `showScreenFlash={false}`

### Performance Issues

**Problem**: App becomes slow with visual feedback

**Solutions**:
1. Disable element boxes if not needed
2. Reduce `boxOpacity` for lighter rendering
3. Limit number of elements shown
4. Use visual feedback only during debugging

## Examples

### Minimal Setup

```typescript
import { DebugVisualFeedback } from 'que-mobile-sdk';

function App() {
  return (
    <View style={{ flex: 1 }}>
      <MyContent />
      <DebugVisualFeedback enabled={__DEV__} />
    </View>
  );
}
```

### Full-Featured Setup

```typescript
import { DebugVisualFeedback, useAgent } from 'que-mobile-sdk';

function App() {
  const [screenState, setScreenState] = React.useState(null);
  const [lastAction, setLastAction] = React.useState(null);
  const [showDebug, setShowDebug] = React.useState(__DEV__);

  const { execute } = useAgent({
    apiKey: process.env.GEMINI_API_KEY!,
    onStep: (step) => {
      setScreenState(step.screenState);
      setLastAction(step.lastAction);
    },
  });

  return (
    <View style={{ flex: 1 }}>
      {/* Debug toggle */}
      <Button
        title={showDebug ? "Hide Debug" : "Show Debug"}
        onPress={() => setShowDebug(!showDebug)}
      />
      
      {/* Main content */}
      <MyContent />
      
      {/* Visual feedback */}
      <DebugVisualFeedback
        enabled={showDebug}
        screenState={screenState}
        lastAction={lastAction}
        boxOpacity={0.4}
        tapIndicatorSize={60}
        showElementBoxes={true}
        showTapIndicators={true}
        showScreenFlash={true}
      />
    </View>
  );
}
```

### Custom Styling

```typescript
import { DebugVisualFeedback } from 'que-mobile-sdk';

function App() {
  return (
    <View style={{ flex: 1 }}>
      <MyContent />
      
      {/* Custom styled debug feedback */}
      <DebugVisualFeedback
        enabled={true}
        screenState={screenState}
        lastAction={lastAction}
        boxOpacity={0.6}           // More visible
        tapIndicatorSize={100}     // Larger indicators
        showScreenFlash={false}    // No flash
      />
    </View>
  );
}
```

## Best Practices

### 1. Enable Only in Development

```typescript
<DebugVisualFeedback enabled={__DEV__} />
```

### 2. Provide Complete State

```typescript
<DebugVisualFeedback
  enabled={true}
  screenState={screenState}  // Always provide current state
  lastAction={lastAction}    // Always provide last action
/>
```

### 3. Use with Debug Overlay

Combine with `DebugOverlay` for complete debugging:

```typescript
<View>
  <DebugVisualFeedback enabled={true} screenState={screenState} lastAction={lastAction} />
  <DebugOverlay step={5} maxSteps={100} lastAction="tap_element" elementCount={42} reasoning="..." />
</View>
```

### 4. Toggle Visibility

Allow users to toggle debug features:

```typescript
const [debugEnabled, setDebugEnabled] = React.useState(__DEV__);

<Button title="Toggle Debug" onPress={() => setDebugEnabled(!debugEnabled)} />
<DebugVisualFeedback enabled={debugEnabled} />
```

---

For more information, see:
- [README](../README.md)
- [API Documentation](./API.md)
- [Debug Mode](./DEBUG_MODE.md)
- [Usage Guides](./GUIDES.md)

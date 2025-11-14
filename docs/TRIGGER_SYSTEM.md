# QUE Mobile SDK - Trigger System

Comprehensive guide to automated task execution with scheduled and event-based triggers.

## Table of Contents

- [Overview](#overview)
- [Trigger Types](#trigger-types)
- [Setup Guide](#setup-guide)
- [API Reference](#api-reference)
- [Examples](#examples)
- [Boot Persistence](#boot-persistence)
- [Best Practices](#best-practices)

## Overview

The Trigger System enables automated task execution without manual intervention. Tasks can be triggered by:

- **Schedule**: Execute tasks at specific times and days
- **Notifications**: Execute tasks when specific app notifications arrive

### Key Features

- ⏰ **Scheduled Triggers**: Run tasks daily, weekly, or on custom schedules
- 🔔 **Notification Triggers**: React to app notifications automatically
- 🔄 **Boot Persistence**: Triggers survive device reboots
- 📊 **Priority System**: Control execution order when multiple triggers fire
- 📝 **Execution History**: Track trigger performance and results
- 🎯 **Pattern Matching**: Filter notifications by title and text patterns

### Architecture

```
┌─────────────────────────────────────────────────┐
│           TriggerManager                         │
│  (Orchestrates all trigger types)               │
└─────────────────────────────────────────────────┘
                      ↓
┌──────────────┬──────────────┬──────────────────┐
│ScheduleTrigger│NotificationTrigger│BootReceiver│
│ (AlarmManager)│(NotificationListener)│(Persistence)│
└──────────────┴──────────────┴──────────────────┘
```

## Trigger Types

### Schedule Triggers

Execute tasks at specific times on selected days of the week.

**Use Cases:**
- Daily morning briefings
- Weekly report generation
- Scheduled reminders
- Automated data collection
- Periodic app interactions

**Configuration:**

```typescript
interface ScheduleTriggerConfig {
  id: string;
  type: 'schedule';
  enabled: boolean;
  priority: number;
  task: string;
  agentConfig: AgentConfig;
  schedule: {
    time: string;           // Format: "HH:mm" (24-hour)
    daysOfWeek: number[];   // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
    timezone?: string;      // Optional timezone
  };
}
```

**Example:**

```typescript
const morningBriefing: ScheduleTriggerConfig = {
  id: 'morning-briefing',
  type: 'schedule',
  enabled: true,
  priority: 10,
  task: 'Open Calendar, check today\'s events, and speak a summary',
  agentConfig: {
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 50,
  },
  schedule: {
    time: '08:00',
    daysOfWeek: [1, 2, 3, 4, 5], // Monday to Friday
  },
};
```

### Notification Triggers

Execute tasks when specific app notifications arrive.

**Use Cases:**
- Auto-reply to messages
- Process incoming emails
- React to social media notifications
- Monitor app events

**Configuration:**

```typescript
interface NotificationTriggerConfig {
  id: string;
  type: 'notification';
  enabled: boolean;
  priority: number;
  task: string;
  agentConfig: AgentConfig;
  notificationConfig: {
    packageName: string;      // App package name
    titlePattern?: string;    // Optional title filter
    textPattern?: string;     // Optional text filter
  };
}
```

**Example:**

```typescript
const emailNotification: NotificationTriggerConfig = {
  id: 'email-auto-reply',
  type: 'notification',
  enabled: true,
  priority: 5,
  task: 'Read the email notification, and if it\'s from my boss, open Gmail and reply "I\'ll look into this"',
  agentConfig: {
    apiKey: process.env.GEMINI_API_KEY!,
    maxSteps: 100,
  },
  notificationConfig: {
    packageName: 'com.google.android.gm',
    titlePattern: 'New email',
  },
};
```

## Setup Guide

### 1. Install Dependencies

The Trigger System is included in the QUE Mobile SDK. No additional installation required.

```bash
npm install que-mobile-sdk
```

### 2. Configure Expo Plugin

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

The plugin automatically configures:
- `BIND_ACCESSIBILITY_SERVICE` permission
- `RECEIVE_BOOT_COMPLETED` permission (for boot persistence)
- `BIND_NOTIFICATION_LISTENER_SERVICE` permission (for notification triggers)
- Accessibility Service registration
- Notification Listener Service registration
- Boot Receiver registration

### 3. Prebuild Native Code

```bash
npx expo prebuild --clean
```

### 4. Enable Required Services

On your Android device:

#### Enable Accessibility Service
1. Go to **Settings → Accessibility**
2. Find **QUE Accessibility Service**
3. Enable it

#### Enable Notification Access (for notification triggers)
1. Go to **Settings → Apps & notifications → Special app access**
2. Select **Notification access**
3. Find your app and enable it

### 5. Initialize Trigger System

```typescript
import { useTriggers } from 'que-mobile-sdk';

function App() {
  const triggers = useTriggers({
    autoInitialize: true,
    onExecutionStart: (triggerId) => {
      console.log('Trigger started:', triggerId);
    },
    onExecutionComplete: (triggerId, result) => {
      console.log('Trigger completed:', triggerId, result);
    },
    onExecutionError: (triggerId, error) => {
      console.error('Trigger error:', triggerId, error);
    },
  });

  return <YourApp />;
}
```

## API Reference

### useTriggers Hook

Main hook for managing triggers.

```typescript
function useTriggers(config?: UseTriggersConfig): UseTriggers
```

**Configuration:**

```typescript
interface UseTriggersConfig {
  autoInitialize?: boolean;
  onExecutionStart?: (triggerId: string) => void;
  onExecutionComplete?: (triggerId: string, result: AgentResult) => void;
  onExecutionError?: (triggerId: string, error: Error) => void;
}
```

**Returns:**

```typescript
interface UseTriggers {
  // CRUD operations
  createTrigger: (config: TriggerConfig) => Promise<string>;
  updateTrigger: (id: string, updates: Partial<TriggerConfig>) => Promise<void>;
  deleteTrigger: (id: string) => Promise<void>;
  
  // Control
  enableTrigger: (id: string) => Promise<void>;
  disableTrigger: (id: string) => Promise<void>;
  
  // Queries
  getTrigger: (id: string) => Promise<TriggerConfig | null>;
  listTriggers: () => Promise<TriggerConfig[]>;
  getEnabledTriggers: () => Promise<TriggerConfig[]>;
  getTriggersByType: (type: 'schedule' | 'notification') => Promise<TriggerConfig[]>;
  
  // Status and history
  getTriggerStatus: (id: string) => Promise<TriggerStatus | null>;
  getTriggerHistory: (id: string, limit?: number) => Promise<TriggerHistoryItem[]>;
  
  // Manual execution
  executeTrigger: (id: string) => Promise<AgentResult | null>;
  
  // State
  triggers: TriggerConfig[];
  isLoading: boolean;
  error: string | null;
  
  // Refresh
  refresh: () => Promise<void>;
}
```

### TriggerManager Class

Low-level trigger management (advanced usage).

```typescript
class TriggerManager {
  constructor()
  
  async createTrigger(config: TriggerConfig): Promise<string>
  async updateTrigger(id: string, updates: Partial<TriggerConfig>): Promise<void>
  async deleteTrigger(id: string): Promise<void>
  async enableTrigger(id: string): Promise<void>
  async disableTrigger(id: string): Promise<void>
  async listTriggers(): Promise<TriggerConfig[]>
  async getTrigger(id: string): Promise<TriggerConfig | null>
  async getTriggerStatus(id: string): Promise<TriggerStatus | null>
  async getTriggerHistory(id: string, limit?: number): Promise<TriggerHistoryItem[]>
  
  onTriggerExecution(callback: (event: TriggerExecutionEvent) => void): () => void
  
  static generateTriggerId(): string
}
```

### ScheduleTrigger Class

Helper class for schedule triggers.

```typescript
class ScheduleTrigger {
  constructor(config: ScheduleTriggerConfig)
  
  async schedule(): Promise<boolean>
  async cancel(): Promise<boolean>
  async getNextExecutionTime(): Promise<number>
  
  static validateSchedule(schedule: ScheduleConfig): boolean
  static createDailySchedule(time: string): ScheduleConfig
  static createWeekdaySchedule(time: string): ScheduleConfig
  static createWeekendSchedule(time: string): ScheduleConfig
  static createCustomSchedule(time: string, daysOfWeek: number[]): ScheduleConfig
}
```

### NotificationTrigger Class

Helper class for notification triggers.

```typescript
class NotificationTrigger {
  constructor(config: NotificationTriggerConfig)
  
  matches(packageName: string, title: string, text: string): boolean
  
  static validateNotificationConfig(config: NotificationConfig): boolean
  static createForApp(packageName: string): NotificationConfig
  static createWithTitlePattern(packageName: string, titlePattern: string): NotificationConfig
  static createWithTextPattern(packageName: string, textPattern: string): NotificationConfig
  static createWithPatterns(packageName: string, titlePattern: string, textPattern: string): NotificationConfig
}
```

### Types

```typescript
interface TriggerStatus {
  id: string;
  enabled: boolean;
  lastExecuted?: number;
  nextExecution?: number;
  executionCount: number;
  lastError?: string;
}

interface TriggerHistoryItem {
  triggerId: string;
  executedAt: number;
  success: boolean;
  error?: string;
  result?: any;
}

interface TriggerExecutionEvent {
  triggerId: string;
  triggerType: 'schedule' | 'notification';
  timestamp: number;
  metadata?: {
    notificationPackage?: string;
    notificationTitle?: string;
    notificationText?: string;
  };
}
```

## Examples

### Basic Schedule Trigger

Create a simple daily reminder.

```typescript
import { useTriggers, TriggerManager } from 'que-mobile-sdk';

function DailyReminderScreen() {
  const { createTrigger, triggers, isLoading } = useTriggers();

  const handleCreateReminder = async () => {
    const triggerId = TriggerManager.generateTriggerId();
    
    await createTrigger({
      id: triggerId,
      type: 'schedule',
      enabled: true,
      priority: 10,
      task: 'Speak "Time to take your medication"',
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 10,
      },
      schedule: {
        time: '09:00',
        daysOfWeek: [0, 1, 2, 3, 4, 5, 6], // Every day
      },
    });
    
    alert('Daily reminder created!');
  };

  return (
    <View style={{ padding: 20 }}>
      <Button title="Create Daily Reminder" onPress={handleCreateReminder} />
      
      {triggers.map(trigger => (
        <Text key={trigger.id}>{trigger.task}</Text>
      ))}
    </View>
  );
}
```

### Weekday Morning Briefing

Schedule a task for weekday mornings.

```typescript
import { useTriggers, ScheduleTrigger } from 'que-mobile-sdk';

function MorningBriefingScreen() {
  const { createTrigger } = useTriggers();

  const handleCreateBriefing = async () => {
    await createTrigger({
      id: 'morning-briefing',
      type: 'schedule',
      enabled: true,
      priority: 10,
      task: `
        1. Open Calendar app
        2. Check today's events
        3. Create a file called today_schedule.md with the events
        4. Speak "Good morning! Here's your schedule for today"
        5. Read the schedule aloud
      `,
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 100,
      },
      schedule: ScheduleTrigger.createWeekdaySchedule('08:00'),
    });
    
    alert('Morning briefing scheduled for 8 AM on weekdays!');
  };

  return <Button title="Schedule Morning Briefing" onPress={handleCreateBriefing} />;
}
```

### Custom Schedule

Create a trigger for specific days.

```typescript
function CustomScheduleScreen() {
  const { createTrigger } = useTriggers();

  const handleCreateCustom = async () => {
    // Every Monday, Wednesday, Friday at 2:30 PM
    await createTrigger({
      id: 'workout-reminder',
      type: 'schedule',
      enabled: true,
      priority: 5,
      task: 'Speak "Time for your workout!"',
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 10,
      },
      schedule: ScheduleTrigger.createCustomSchedule('14:30', [1, 3, 5]),
    });
    
    alert('Workout reminder scheduled!');
  };

  return <Button title="Schedule Workout Reminder" onPress={handleCreateCustom} />;
}
```

### Notification Trigger for Messages

React to incoming messages automatically.

```typescript
import { useTriggers, NotificationTrigger } from 'que-mobile-sdk';

function AutoReplyScreen() {
  const { createTrigger } = useTriggers();

  const handleCreateAutoReply = async () => {
    await createTrigger({
      id: 'whatsapp-auto-reply',
      type: 'notification',
      enabled: true,
      priority: 10,
      task: `
        1. Open WhatsApp
        2. Read the latest message
        3. If it's from "John", reply "I'm in a meeting, will respond soon"
        4. Return to home screen
      `,
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 50,
      },
      notificationConfig: NotificationTrigger.createForApp('com.whatsapp'),
    });
    
    alert('Auto-reply enabled for WhatsApp!');
  };

  return <Button title="Enable Auto-Reply" onPress={handleCreateAutoReply} />;
}
```

### Notification with Pattern Matching

Filter notifications by title and text patterns.

```typescript
function EmailFilterScreen() {
  const { createTrigger } = useTriggers();

  const handleCreateEmailFilter = async () => {
    await createTrigger({
      id: 'urgent-email-alert',
      type: 'notification',
      enabled: true,
      priority: 20, // High priority
      task: `
        1. Speak "You have an urgent email"
        2. Open Gmail
        3. Read the email subject and sender
        4. Speak the details
      `,
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 50,
      },
      notificationConfig: NotificationTrigger.createWithPatterns(
        'com.google.android.gm',
        'New email',
        'URGENT'
      ),
    });
    
    alert('Urgent email alert enabled!');
  };

  return <Button title="Enable Urgent Email Alerts" onPress={handleCreateEmailFilter} />;
}
```

### Managing Triggers

Complete trigger management UI.

```typescript
function TriggerManagementScreen() {
  const {
    triggers,
    createTrigger,
    updateTrigger,
    deleteTrigger,
    enableTrigger,
    disableTrigger,
    getTriggerStatus,
    isLoading,
    refresh,
  } = useTriggers();

  const [selectedTrigger, setSelectedTrigger] = React.useState<string | null>(null);
  const [status, setStatus] = React.useState<TriggerStatus | null>(null);

  const handleToggleTrigger = async (id: string, enabled: boolean) => {
    if (enabled) {
      await disableTrigger(id);
    } else {
      await enableTrigger(id);
    }
    await refresh();
  };

  const handleDeleteTrigger = async (id: string) => {
    Alert.alert(
      'Delete Trigger',
      'Are you sure you want to delete this trigger?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            await deleteTrigger(id);
            await refresh();
          },
        },
      ]
    );
  };

  const handleViewStatus = async (id: string) => {
    const triggerStatus = await getTriggerStatus(id);
    setStatus(triggerStatus);
    setSelectedTrigger(id);
  };

  return (
    <ScrollView style={{ padding: 20 }}>
      <Text style={{ fontSize: 24, marginBottom: 20 }}>Triggers</Text>
      
      {isLoading && <Text>Loading...</Text>}
      
      {triggers.map(trigger => (
        <View key={trigger.id} style={{ marginBottom: 20, padding: 15, backgroundColor: '#f0f0f0', borderRadius: 8 }}>
          <Text style={{ fontWeight: 'bold' }}>{trigger.id}</Text>
          <Text>{trigger.task}</Text>
          <Text>Type: {trigger.type}</Text>
          <Text>Priority: {trigger.priority}</Text>
          <Text>Status: {trigger.enabled ? '✅ Enabled' : '❌ Disabled'}</Text>
          
          <View style={{ flexDirection: 'row', marginTop: 10 }}>
            <Button
              title={trigger.enabled ? 'Disable' : 'Enable'}
              onPress={() => handleToggleTrigger(trigger.id, trigger.enabled)}
            />
            <Button title="Status" onPress={() => handleViewStatus(trigger.id)} />
            <Button title="Delete" onPress={() => handleDeleteTrigger(trigger.id)} color="red" />
          </View>
        </View>
      ))}
      
      {selectedTrigger && status && (
        <View style={{ marginTop: 20, padding: 15, backgroundColor: '#e0e0e0', borderRadius: 8 }}>
          <Text style={{ fontWeight: 'bold' }}>Status for {selectedTrigger}</Text>
          <Text>Execution Count: {status.executionCount}</Text>
          <Text>Last Executed: {status.lastExecuted ? new Date(status.lastExecuted).toLocaleString() : 'Never'}</Text>
          <Text>Next Execution: {status.nextExecution ? new Date(status.nextExecution).toLocaleString() : 'N/A'}</Text>
          {status.lastError && <Text style={{ color: 'red' }}>Last Error: {status.lastError}</Text>}
        </View>
      )}
      
      <Button title="Refresh" onPress={refresh} />
    </ScrollView>
  );
}
```

### Trigger History

View execution history for a trigger.

```typescript
function TriggerHistoryScreen({ triggerId }: { triggerId: string }) {
  const { getTriggerHistory } = useTriggers();
  const [history, setHistory] = React.useState<TriggerHistoryItem[]>([]);

  React.useEffect(() => {
    loadHistory();
  }, [triggerId]);

  const loadHistory = async () => {
    const items = await getTriggerHistory(triggerId, 20);
    setHistory(items);
  };

  return (
    <ScrollView style={{ padding: 20 }}>
      <Text style={{ fontSize: 24, marginBottom: 20 }}>Execution History</Text>
      
      {history.map((item, index) => (
        <View key={index} style={{ marginBottom: 15, padding: 10, backgroundColor: '#f5f5f5', borderRadius: 5 }}>
          <Text>Executed: {new Date(item.executedAt).toLocaleString()}</Text>
          <Text>Status: {item.success ? '✅ Success' : '❌ Failed'}</Text>
          {item.error && <Text style={{ color: 'red' }}>Error: {item.error}</Text>}
          {item.result && <Text>Result: {JSON.stringify(item.result, null, 2)}</Text>}
        </View>
      ))}
      
      {history.length === 0 && <Text>No execution history yet</Text>}
    </ScrollView>
  );
}
```

### Manual Trigger Execution

Test triggers manually before scheduling.

```typescript
function ManualExecutionScreen() {
  const { executeTrigger, getTrigger } = useTriggers();
  const [result, setResult] = React.useState<AgentResult | null>(null);
  const [isExecuting, setIsExecuting] = React.useState(false);

  const handleExecute = async (triggerId: string) => {
    try {
      setIsExecuting(true);
      const trigger = await getTrigger(triggerId);
      
      if (!trigger) {
        alert('Trigger not found');
        return;
      }
      
      console.log('Executing trigger:', trigger.task);
      const executionResult = await executeTrigger(triggerId);
      setResult(executionResult);
      
      if (executionResult?.success) {
        alert('Trigger executed successfully!');
      } else {
        alert('Trigger execution failed');
      }
    } catch (error) {
      console.error('Execution error:', error);
      alert('Error executing trigger');
    } finally {
      setIsExecuting(false);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Button
        title={isExecuting ? 'Executing...' : 'Test Trigger'}
        onPress={() => handleExecute('morning-briefing')}
        disabled={isExecuting}
      />
      
      {result && (
        <View style={{ marginTop: 20 }}>
          <Text>Result: {result.message}</Text>
          <Text>Steps: {result.steps}</Text>
          <Text>Success: {result.success ? 'Yes' : 'No'}</Text>
        </View>
      )}
    </View>
  );
}
```

### Priority-Based Execution

Handle multiple triggers with priorities.

```typescript
function PriorityTriggersScreen() {
  const { createTrigger } = useTriggers();

  const handleCreatePriorityTriggers = async () => {
    // High priority - urgent notifications
    await createTrigger({
      id: 'urgent-alerts',
      type: 'notification',
      enabled: true,
      priority: 100, // Highest priority
      task: 'Handle urgent notification immediately',
      agentConfig: { apiKey: process.env.GEMINI_API_KEY!, maxSteps: 50 },
      notificationConfig: NotificationTrigger.createWithTextPattern('com.app', 'URGENT'),
    });

    // Medium priority - regular notifications
    await createTrigger({
      id: 'regular-alerts',
      type: 'notification',
      enabled: true,
      priority: 50,
      task: 'Handle regular notification',
      agentConfig: { apiKey: process.env.GEMINI_API_KEY!, maxSteps: 30 },
      notificationConfig: NotificationTrigger.createForApp('com.app'),
    });

    // Low priority - scheduled tasks
    await createTrigger({
      id: 'daily-cleanup',
      type: 'schedule',
      enabled: true,
      priority: 10,
      task: 'Perform daily cleanup tasks',
      agentConfig: { apiKey: process.env.GEMINI_API_KEY!, maxSteps: 100 },
      schedule: { time: '23:00', daysOfWeek: [0, 1, 2, 3, 4, 5, 6] },
    });

    alert('Priority triggers created!');
  };

  return <Button title="Create Priority Triggers" onPress={handleCreatePriorityTriggers} />;
}
```

## Boot Persistence

Triggers automatically persist across device reboots.

### How It Works

1. **Trigger Storage**: All trigger configurations are stored in a SQLite database
2. **Boot Receiver**: A BroadcastReceiver listens for `BOOT_COMPLETED` events
3. **Automatic Restoration**: On boot, all enabled triggers are automatically rescheduled

### Configuration

Boot persistence is automatically configured by the Expo plugin. No additional setup required.

**Permissions Added:**

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

**Receiver Registration:**

```xml
<receiver
    android:name=".triggers.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

### Testing Boot Persistence

```typescript
function BootPersistenceTestScreen() {
  const { createTrigger, listTriggers } = useTriggers();

  const handleCreatePersistentTrigger = async () => {
    await createTrigger({
      id: 'boot-test-trigger',
      type: 'schedule',
      enabled: true,
      priority: 10,
      task: 'Speak "Device has been rebooted and triggers are active"',
      agentConfig: {
        apiKey: process.env.GEMINI_API_KEY!,
        maxSteps: 10,
      },
      schedule: {
        time: '09:00',
        daysOfWeek: [0, 1, 2, 3, 4, 5, 6],
      },
    });

    alert('Trigger created! Reboot your device to test persistence.');
  };

  const handleCheckTriggers = async () => {
    const triggers = await listTriggers();
    alert(`Found ${triggers.length} triggers after boot`);
  };

  return (
    <View style={{ padding: 20 }}>
      <Button title="Create Persistent Trigger" onPress={handleCreatePersistentTrigger} />
      <Button title="Check Triggers After Boot" onPress={handleCheckTriggers} />
    </View>
  );
}
```

### Verification

After device reboot:

1. Open your app
2. Check trigger list - all triggers should be present
3. Check trigger status - enabled triggers should show next execution time
4. Wait for scheduled time - trigger should execute automatically

### Troubleshooting Boot Persistence

**Triggers not restored after boot:**

1. Check if `RECEIVE_BOOT_COMPLETED` permission is granted
2. Verify BootReceiver is registered in AndroidManifest.xml
3. Check device logs for boot receiver execution
4. Ensure triggers were properly saved before reboot

**Debug boot receiver:**

```bash
adb logcat | grep BootReceiver
```

## Best Practices

### 1. Use Descriptive IDs

```typescript
// ❌ Bad
const id = 'trigger1';

// ✅ Good
const id = 'morning-briefing-weekdays';
```

### 2. Set Appropriate Priorities

```typescript
// High priority (80-100): Urgent, time-sensitive tasks
priority: 100

// Medium priority (40-79): Regular tasks
priority: 50

// Low priority (0-39): Background tasks
priority: 10
```

### 3. Limit Max Steps

Keep trigger tasks focused and efficient:

```typescript
// ❌ Bad - too many steps
agentConfig: {
  apiKey: API_KEY,
  maxSteps: 500, // Excessive
}

// ✅ Good - reasonable limit
agentConfig: {
  apiKey: API_KEY,
  maxSteps: 50, // Sufficient for most tasks
}
```

### 4. Handle Errors Gracefully

```typescript
const { createTrigger } = useTriggers({
  onExecutionError: (triggerId, error) => {
    console.error(`Trigger ${triggerId} failed:`, error);
    
    // Log to analytics
    Analytics.logError('trigger_execution_failed', {
      triggerId,
      error: error.message,
    });
    
    // Notify user if critical
    if (isCriticalTrigger(triggerId)) {
      showNotification('Trigger Failed', error.message);
    }
  },
});
```

### 5. Test Before Enabling

Always test triggers manually before enabling:

```typescript
const handleCreateTrigger = async () => {
  const triggerId = await createTrigger(config);
  
  // Test execution
  console.log('Testing trigger...');
  const result = await executeTrigger(triggerId);
  
  if (result?.success) {
    // Enable if test succeeds
    await enableTrigger(triggerId);
    alert('Trigger enabled!');
  } else {
    alert('Test failed. Please review the task.');
  }
};
```

### 6. Use Pattern Matching Wisely

Be specific with notification patterns:

```typescript
// ❌ Too broad - matches too many notifications
notificationConfig: {
  packageName: 'com.google.android.gm',
  titlePattern: 'New', // Matches "New email", "New message", etc.
}

// ✅ Specific - matches only what you need
notificationConfig: {
  packageName: 'com.google.android.gm',
  titlePattern: 'New email from',
  textPattern: 'boss@company.com',
}
```

### 7. Monitor Execution History

Regularly check trigger performance:

```typescript
const handleCheckPerformance = async () => {
  const history = await getTriggerHistory('my-trigger', 50);
  
  const successRate = history.filter(h => h.success).length / history.length;
  console.log(`Success rate: ${(successRate * 100).toFixed(1)}%`);
  
  if (successRate < 0.8) {
    alert('Trigger has low success rate. Consider reviewing the task.');
  }
};
```

### 8. Clean Up Unused Triggers

Remove triggers you no longer need:

```typescript
const handleCleanup = async () => {
  const triggers = await listTriggers();
  
  for (const trigger of triggers) {
    const status = await getTriggerStatus(trigger.id);
    
    // Remove triggers that haven't executed in 30 days
    if (status?.lastExecuted && Date.now() - status.lastExecuted > 30 * 24 * 60 * 60 * 1000) {
      await deleteTrigger(trigger.id);
      console.log(`Removed inactive trigger: ${trigger.id}`);
    }
  }
};
```

### 9. Avoid Overlapping Schedules

Prevent multiple triggers from executing simultaneously:

```typescript
// ❌ Bad - both execute at 9 AM
const trigger1 = { schedule: { time: '09:00', daysOfWeek: [1, 2, 3, 4, 5] } };
const trigger2 = { schedule: { time: '09:00', daysOfWeek: [1, 2, 3, 4, 5] } };

// ✅ Good - stagger execution times
const trigger1 = { schedule: { time: '09:00', daysOfWeek: [1, 2, 3, 4, 5] } };
const trigger2 = { schedule: { time: '09:15', daysOfWeek: [1, 2, 3, 4, 5] } };
```

### 10. Use Callbacks for Monitoring

Track trigger execution in real-time:

```typescript
const { createTrigger } = useTriggers({
  onExecutionStart: (triggerId) => {
    console.log(`[${new Date().toISOString()}] Trigger started: ${triggerId}`);
    showNotification('Task Started', `Executing ${triggerId}`);
  },
  onExecutionComplete: (triggerId, result) => {
    console.log(`[${new Date().toISOString()}] Trigger completed: ${triggerId}`);
    if (result.success) {
      showNotification('Task Completed', result.message);
    }
  },
  onExecutionError: (triggerId, error) => {
    console.error(`[${new Date().toISOString()}] Trigger failed: ${triggerId}`, error);
    showNotification('Task Failed', error.message);
  },
});
```

## Common Use Cases

### 1. Daily Morning Routine

```typescript
await createTrigger({
  id: 'morning-routine',
  type: 'schedule',
  enabled: true,
  priority: 10,
  task: `
    1. Speak "Good morning!"
    2. Open Weather app and check today's forecast
    3. Open Calendar and check today's events
    4. Create a file called daily_briefing.md with weather and schedule
    5. Speak the briefing
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 100 },
  schedule: { time: '07:00', daysOfWeek: [1, 2, 3, 4, 5] },
});
```

### 2. Auto-Reply to Important Messages

```typescript
await createTrigger({
  id: 'auto-reply-boss',
  type: 'notification',
  enabled: true,
  priority: 100,
  task: `
    1. Open messaging app
    2. Read the message
    3. Reply "I'm currently unavailable. I'll respond as soon as possible."
    4. Return to home screen
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 50 },
  notificationConfig: {
    packageName: 'com.whatsapp',
    titlePattern: 'Boss',
  },
});
```

### 3. Weekly Report Generation

```typescript
await createTrigger({
  id: 'weekly-report',
  type: 'schedule',
  enabled: true,
  priority: 5,
  task: `
    1. Create a file called weekly_report.md
    2. Write "# Weekly Report" as header
    3. Open fitness app and collect step count for the week
    4. Append step count to report
    5. Open calendar and collect number of meetings
    6. Append meeting count to report
    7. Speak "Weekly report generated"
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 150 },
  schedule: { time: '18:00', daysOfWeek: [5] }, // Friday evening
});
```

### 4. Social Media Automation

```typescript
await createTrigger({
  id: 'instagram-engagement',
  type: 'schedule',
  enabled: true,
  priority: 3,
  task: `
    1. Open Instagram
    2. Search for "reactnative"
    3. Like the first 5 posts
    4. Comment "Great content!" on the first post
    5. Return to home screen
    6. Report completion
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 100 },
  schedule: { time: '12:00', daysOfWeek: [1, 3, 5] },
});
```

### 5. Email Monitoring

```typescript
await createTrigger({
  id: 'urgent-email-monitor',
  type: 'notification',
  enabled: true,
  priority: 90,
  task: `
    1. Open Gmail
    2. Read the email subject and sender
    3. If subject contains "URGENT", speak "You have an urgent email from [sender]"
    4. Create a file called urgent_emails.md
    5. Append email details to the file
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 80 },
  notificationConfig: {
    packageName: 'com.google.android.gm',
    textPattern: 'URGENT',
  },
});
```

### 6. Reminder System

```typescript
await createTrigger({
  id: 'medication-reminder',
  type: 'schedule',
  enabled: true,
  priority: 100,
  task: 'Speak "Time to take your medication" three times with 5 second pauses',
  agentConfig: { apiKey: API_KEY, maxSteps: 20 },
  schedule: { time: '09:00', daysOfWeek: [0, 1, 2, 3, 4, 5, 6] },
});

await createTrigger({
  id: 'water-reminder',
  type: 'schedule',
  enabled: true,
  priority: 50,
  task: 'Speak "Remember to drink water"',
  agentConfig: { apiKey: API_KEY, maxSteps: 10 },
  schedule: { time: '10:00', daysOfWeek: [0, 1, 2, 3, 4, 5, 6] },
});
```

## Troubleshooting

### Triggers Not Executing

**Check Accessibility Service:**
```typescript
// Verify service is enabled
const isEnabled = await AccessibilityModule.isServiceEnabled();
if (!isEnabled) {
  alert('Please enable Accessibility Service in Settings');
}
```

**Check Trigger Status:**
```typescript
const status = await getTriggerStatus('my-trigger');
console.log('Enabled:', status?.enabled);
console.log('Next execution:', status?.nextExecution);
console.log('Last error:', status?.lastError);
```

**Check Notification Access:**
For notification triggers, ensure notification access is granted in device settings.

### Schedule Not Working

**Verify Time Format:**
```typescript
// ❌ Wrong
schedule: { time: '9:00 AM', daysOfWeek: [1] }

// ✅ Correct
schedule: { time: '09:00', daysOfWeek: [1] }
```

**Check Days of Week:**
```typescript
// Remember: 0 = Sunday, 1 = Monday, ..., 6 = Saturday
schedule: { time: '09:00', daysOfWeek: [1, 2, 3, 4, 5] } // Weekdays
```

### Notification Triggers Not Firing

**Verify Package Name:**
```bash
# Find package name
adb shell pm list packages | grep <app_name>
```

**Test Pattern Matching:**
```typescript
const trigger = new NotificationTrigger(config);
const matches = trigger.matches('com.app', 'Test Title', 'Test Text');
console.log('Pattern matches:', matches);
```

### High Battery Usage

**Reduce Trigger Frequency:**
```typescript
// Instead of every hour
schedule: { time: '09:00', daysOfWeek: [0, 1, 2, 3, 4, 5, 6] }

// Use once per day
schedule: { time: '09:00', daysOfWeek: [0, 1, 2, 3, 4, 5, 6] }
```

**Optimize Task Complexity:**
```typescript
// Keep maxSteps reasonable
agentConfig: {
  apiKey: API_KEY,
  maxSteps: 30, // Lower is better for battery
}
```

## Advanced Topics

### Custom Trigger Types

Extend the trigger system with custom types:

```typescript
interface CustomTriggerConfig extends BaseTriggerConfig {
  type: 'custom';
  customConfig: {
    condition: string;
    checkInterval: number;
  };
}

// Implement custom trigger logic
class CustomTriggerManager {
  async checkCondition(config: CustomTriggerConfig): Promise<boolean> {
    // Your custom logic here
    return true;
  }
}
```

### Trigger Chaining

Execute triggers in sequence:

```typescript
const { executeTrigger } = useTriggers();

const handleChainedExecution = async () => {
  // Execute triggers in order
  await executeTrigger('data-collection');
  await executeTrigger('data-processing');
  await executeTrigger('report-generation');
};
```

### Conditional Triggers

Create triggers with conditions:

```typescript
await createTrigger({
  id: 'conditional-trigger',
  type: 'schedule',
  enabled: true,
  priority: 10,
  task: `
    1. Check if it's raining (open weather app)
    2. If raining, speak "Don't forget your umbrella"
    3. If not raining, speak "Have a great day"
  `,
  agentConfig: { apiKey: API_KEY, maxSteps: 50 },
  schedule: { time: '07:00', daysOfWeek: [1, 2, 3, 4, 5] },
});
```

---

## Related Documentation

- [API Documentation](./API.md)
- [Usage Guides](./GUIDES.md)
- [Expo Plugin](./EXPO_PLUGIN.md)
- [README](../README.md)

---

## Support

For issues or questions about the Trigger System:

1. Check the [troubleshooting section](#troubleshooting)
2. Review [examples](#examples)
3. Open an issue on [GitHub](https://github.com/yourusername/que-mobile-sdk/issues)

---

**Last Updated:** November 2024

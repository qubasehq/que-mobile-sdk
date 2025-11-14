import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Switch,
} from 'react-native';
import { useAgent, DebugOverlay } from 'que-mobile-sdk';

export default function DebugModeScreen() {
  const [apiKey, setApiKey] = useState('');
  const [task, setTask] = useState('Open Settings and enable WiFi');
  const [debugEnabled, setDebugEnabled] = useState(true);
  const [showOverlay, setShowOverlay] = useState(true);

  const { execute, isRunning, result, error, stop } = useAgent({
    apiKey: apiKey || 'YOUR_GEMINI_API_KEY',
    maxSteps: 30,
    debugMode: debugEnabled,
  });

  const handleExecute = async () => {
    if (!task.trim()) {
      return;
    }
    
    try {
      await execute(task);
    } catch (err) {
      console.error('Task execution error:', err);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView}>
        <View style={styles.content}>
          <Text style={styles.title}>Debug Mode</Text>
          <Text style={styles.description}>
            Enable debug mode to see visual feedback during task execution. The debug overlay shows real-time information about agent actions, perception, and decision-making.
          </Text>

          <View style={styles.settingsSection}>
            <View style={styles.settingRow}>
              <View style={styles.settingInfo}>
                <Text style={styles.settingLabel}>Debug Mode</Text>
                <Text style={styles.settingDescription}>
                  Show detailed execution information
                </Text>
              </View>
              <Switch
                value={debugEnabled}
                onValueChange={setDebugEnabled}
                disabled={isRunning}
              />
            </View>

            <View style={styles.settingRow}>
              <View style={styles.settingInfo}>
                <Text style={styles.settingLabel}>Debug Overlay</Text>
                <Text style={styles.settingDescription}>
                  Display floating debug panel
                </Text>
              </View>
              <Switch
                value={showOverlay}
                onValueChange={setShowOverlay}
              />
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>Gemini API Key (Optional)</Text>
            <TextInput
              style={styles.input}
              value={apiKey}
              onChangeText={setApiKey}
              placeholder="Enter your Gemini API key"
              secureTextEntry
              autoCapitalize="none"
              editable={!isRunning}
            />
          </View>

          <View style={styles.section}>
            <Text style={styles.label}>Task</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              value={task}
              onChangeText={setTask}
              placeholder="Enter task to execute"
              multiline
              numberOfLines={3}
              editable={!isRunning}
            />
          </View>

          <View style={styles.examplesSection}>
            <Text style={styles.examplesTitle}>Example Tasks:</Text>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('Open Settings and enable WiFi')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>Open Settings and enable WiFi</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('Navigate to Display settings and increase brightness')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>Navigate to Display settings</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('Open Chrome, search for "React Native", and click the first result')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>Multi-step browser task</Text>
            </TouchableOpacity>
          </View>

          {!isRunning ? (
            <TouchableOpacity
              style={styles.executeButton}
              onPress={handleExecute}
            >
              <Text style={styles.buttonText}>Execute with Debug Mode</Text>
            </TouchableOpacity>
          ) : (
            <View style={styles.runningContainer}>
              <Text style={styles.runningText}>
                Task is running... Watch the debug overlay for details
              </Text>
              <TouchableOpacity
                style={styles.stopButton}
                onPress={stop}
              >
                <Text style={styles.buttonText}>Stop Execution</Text>
              </TouchableOpacity>
            </View>
          )}

          <View style={styles.infoSection}>
            <Text style={styles.infoTitle}>Debug Information Shown:</Text>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>Current step and action being executed</Text>
            </View>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>Screen perception and element detection</Text>
            </View>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>LLM reasoning and decision-making</Text>
            </View>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>Action parameters and results</Text>
            </View>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>Memory and context updates</Text>
            </View>
            <View style={styles.infoItem}>
              <Text style={styles.infoBullet}>•</Text>
              <Text style={styles.infoText}>Error messages and retry attempts</Text>
            </View>
          </View>

          {error && (
            <View style={styles.errorContainer}>
              <Text style={styles.errorTitle}>Error:</Text>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          {result && (
            <View style={styles.resultContainer}>
              <Text style={styles.resultTitle}>
                Result: {result.success ? '✅ Success' : '❌ Failed'}
              </Text>
              <Text style={styles.resultText}>{result.message}</Text>
              <Text style={styles.resultMeta}>
                Steps: {result.steps} | Files: {result.files.length}
              </Text>
            </View>
          )}
        </View>
      </ScrollView>

      {showOverlay && debugEnabled && (
        <DebugOverlay
          isVisible={isRunning}
          currentStep={0}
          totalSteps={0}
          currentAction=""
          logs={[]}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333',
  },
  description: {
    fontSize: 14,
    color: '#666',
    marginBottom: 20,
    lineHeight: 20,
  },
  settingsSection: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 15,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  settingInfo: {
    flex: 1,
    marginRight: 15,
  },
  settingLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  settingDescription: {
    fontSize: 13,
    color: '#666',
  },
  section: {
    marginBottom: 20,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    color: '#333',
  },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    minHeight: 50,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  examplesSection: {
    marginBottom: 20,
    padding: 15,
    backgroundColor: '#fff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  examplesTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 10,
    color: '#666',
  },
  exampleButton: {
    padding: 10,
    backgroundColor: '#f0f0f0',
    borderRadius: 6,
    marginBottom: 8,
  },
  exampleText: {
    fontSize: 14,
    color: '#007AFF',
  },
  executeButton: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 20,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  runningContainer: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  runningText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    marginBottom: 15,
  },
  stopButton: {
    backgroundColor: '#FF3B30',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    minWidth: 150,
  },
  infoSection: {
    backgroundColor: '#E3F2FD',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#2196F3',
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1976D2',
    marginBottom: 12,
  },
  infoItem: {
    flexDirection: 'row',
    marginBottom: 8,
  },
  infoBullet: {
    fontSize: 14,
    color: '#1976D2',
    marginRight: 8,
    fontWeight: 'bold',
  },
  infoText: {
    flex: 1,
    fontSize: 14,
    color: '#0D47A1',
    lineHeight: 20,
  },
  errorContainer: {
    backgroundColor: '#FFE5E5',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FF3B30',
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FF3B30',
    marginBottom: 5,
  },
  errorText: {
    fontSize: 14,
    color: '#D32F2F',
  },
  resultContainer: {
    backgroundColor: '#E8F5E9',
    padding: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#4CAF50',
  },
  resultTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2E7D32',
    marginBottom: 5,
  },
  resultText: {
    fontSize: 14,
    color: '#1B5E20',
    marginBottom: 8,
  },
  resultMeta: {
    fontSize: 12,
    color: '#558B2F',
  },
});

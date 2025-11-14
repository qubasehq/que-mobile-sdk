import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useAgent } from 'que-mobile-sdk';

export default function SimpleTaskScreen() {
  const [task, setTask] = useState('Open Settings');
  const [apiKey, setApiKey] = useState('');

  const { execute, isRunning, result, error, stop } = useAgent({
    apiKey: apiKey || 'YOUR_GEMINI_API_KEY',
    maxSteps: 20,
    debugMode: false,
  });

  const handleExecute = async () => {
    if (!task.trim()) {
      Alert.alert('Error', 'Please enter a task');
      return;
    }
    
    try {
      await execute(task);
    } catch (err) {
      console.error('Task execution error:', err);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Simple Task Execution</Text>
        <Text style={styles.description}>
          Execute basic automation tasks using natural language commands.
        </Text>

        <View style={styles.section}>
          <Text style={styles.label}>Gemini API Key (Optional)</Text>
          <TextInput
            style={styles.input}
            value={apiKey}
            onChangeText={setApiKey}
            placeholder="Enter your Gemini API key"
            secureTextEntry
            autoCapitalize="none"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.label}>Task</Text>
          <TextInput
            style={styles.input}
            value={task}
            onChangeText={setTask}
            placeholder="Enter task (e.g., 'Open Settings')"
            multiline
            numberOfLines={3}
            editable={!isRunning}
          />
        </View>

        <View style={styles.examplesSection}>
          <Text style={styles.examplesTitle}>Example Tasks:</Text>
          <TouchableOpacity
            style={styles.exampleButton}
            onPress={() => setTask('Open Settings')}
            disabled={isRunning}
          >
            <Text style={styles.exampleText}>Open Settings</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.exampleButton}
            onPress={() => setTask('Go back to home screen')}
            disabled={isRunning}
          >
            <Text style={styles.exampleText}>Go back to home screen</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.exampleButton}
            onPress={() => setTask('Open Chrome and search for React Native')}
            disabled={isRunning}
          >
            <Text style={styles.exampleText}>Open Chrome and search for React Native</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.buttonContainer}>
          {!isRunning ? (
            <TouchableOpacity
              style={styles.executeButton}
              onPress={handleExecute}
            >
              <Text style={styles.buttonText}>Execute Task</Text>
            </TouchableOpacity>
          ) : (
            <View style={styles.runningContainer}>
              <ActivityIndicator size="large" color="#007AFF" />
              <Text style={styles.runningText}>Executing task...</Text>
              <TouchableOpacity
                style={styles.stopButton}
                onPress={stop}
              >
                <Text style={styles.buttonText}>Stop</Text>
              </TouchableOpacity>
            </View>
          )}
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
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
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
  buttonContainer: {
    marginBottom: 20,
  },
  executeButton: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  stopButton: {
    backgroundColor: '#FF3B30',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  runningContainer: {
    alignItems: 'center',
    padding: 20,
  },
  runningText: {
    marginTop: 10,
    fontSize: 16,
    color: '#666',
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

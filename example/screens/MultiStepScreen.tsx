import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { useAgent } from 'que-mobile-sdk';

export default function MultiStepScreen() {
  const [apiKey, setApiKey] = useState('');
  const [task, setTask] = useState(
    'Open Chrome, search for "React Native tutorials", click the first result, and scroll down'
  );

  const { execute, isRunning, result, error, stop } = useAgent({
    apiKey: apiKey || 'YOUR_GEMINI_API_KEY',
    maxSteps: 50,
    debugMode: false,
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
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Multi-Step Complex Tasks</Text>
        <Text style={styles.description}>
          Execute complex, multi-step automation tasks that require multiple actions and decision-making across different apps and screens.
        </Text>

        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>💡 What are Multi-Step Tasks?</Text>
          <Text style={styles.infoText}>
            Multi-step tasks involve a sequence of actions that the agent must perform to achieve a goal. The agent uses perception, reasoning, and memory to navigate through different screens and make decisions.
          </Text>
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
          <Text style={styles.label}>Complex Task</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={task}
            onChangeText={setTask}
            placeholder="Enter a multi-step task"
            multiline
            numberOfLines={4}
            editable={!isRunning}
          />
        </View>

        <View style={styles.examplesSection}>
          <Text style={styles.examplesTitle}>Example Multi-Step Tasks:</Text>

          <TouchableOpacity
            style={styles.exampleCard}
            onPress={() =>
              setTask(
                'Open Chrome, search for "React Native tutorials", click the first result, and scroll down'
              )
            }
            disabled={isRunning}
          >
            <Text style={styles.exampleTitle}>🌐 Web Research</Text>
            <Text style={styles.exampleDescription}>
              Open browser, search, and navigate results
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.exampleCard}
            onPress={() =>
              setTask(
                'Go to Settings, navigate to Display, increase brightness to maximum, then go back to home'
              )
            }
            disabled={isRunning}
          >
            <Text style={styles.exampleTitle}>⚙️ Settings Navigation</Text>
            <Text style={styles.exampleDescription}>
              Navigate through settings and modify configurations
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.exampleCard}
            onPress={() =>
              setTask(
                'Open Gmail, find the latest email from "support", read it, and reply with "Thank you"'
              )
            }
            disabled={isRunning}
          >
            <Text style={styles.exampleTitle}>📧 Email Management</Text>
            <Text style={styles.exampleDescription}>
              Read and respond to emails
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.exampleCard}
            onPress={() =>
              setTask(
                'Open Maps, search for "coffee shops near me", select the first result, and check the reviews'
              )
            }
            disabled={isRunning}
          >
            <Text style={styles.exampleTitle}>🗺️ Location Search</Text>
            <Text style={styles.exampleDescription}>
              Search locations and gather information
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.exampleCard}
            onPress={() =>
              setTask(
                'Open YouTube, search for "React Native animation tutorial", play the first video, and enable captions'
              )
            }
            disabled={isRunning}
          >
            <Text style={styles.exampleTitle}>🎥 Video Playback</Text>
            <Text style={styles.exampleDescription}>
              Search and control video playback
            </Text>
          </TouchableOpacity>
        </View>

        {!isRunning ? (
          <TouchableOpacity style={styles.executeButton} onPress={handleExecute}>
            <Text style={styles.buttonText}>Execute Multi-Step Task</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.runningContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
            <Text style={styles.runningText}>
              Executing multi-step task... This may take a while
            </Text>
            <TouchableOpacity style={styles.stopButton} onPress={stop}>
              <Text style={styles.buttonText}>Stop Execution</Text>
            </TouchableOpacity>
          </View>
        )}

        <View style={styles.featuresSection}>
          <Text style={styles.featuresTitle}>Agent Capabilities:</Text>
          <View style={styles.featureItem}>
            <Text style={styles.featureBullet}>✓</Text>
            <Text style={styles.featureText}>
              Context awareness across multiple screens
            </Text>
          </View>
          <View style={styles.featureItem}>
            <Text style={styles.featureBullet}>✓</Text>
            <Text style={styles.featureText}>
              Decision-making based on screen content
            </Text>
          </View>
          <View style={styles.featureItem}>
            <Text style={styles.featureBullet}>✓</Text>
            <Text style={styles.featureText}>
              Memory of previous actions and results
            </Text>
          </View>
          <View style={styles.featureItem}>
            <Text style={styles.featureBullet}>✓</Text>
            <Text style={styles.featureText}>
              Error recovery and retry mechanisms
            </Text>
          </View>
          <View style={styles.featureItem}>
            <Text style={styles.featureBullet}>✓</Text>
            <Text style={styles.featureText}>
              Cross-app navigation and interaction
            </Text>
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
            <View style={styles.resultStats}>
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Steps Executed</Text>
                <Text style={styles.statValue}>{result.steps}</Text>
              </View>
              <View style={styles.statItem}>
                <Text style={styles.statLabel}>Files Accessed</Text>
                <Text style={styles.statValue}>{result.files.length}</Text>
              </View>
            </View>
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
  infoCard: {
    backgroundColor: '#E8F4FD',
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
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#0D47A1',
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
  textArea: {
    minHeight: 100,
    textAlignVertical: 'top',
  },
  examplesSection: {
    marginBottom: 20,
  },
  examplesTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 15,
    color: '#333',
  },
  exampleCard: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#ddd',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  exampleTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#007AFF',
    marginBottom: 5,
  },
  exampleDescription: {
    fontSize: 14,
    color: '#666',
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
    marginTop: 10,
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
  stopButton: {
    backgroundColor: '#FF3B30',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 15,
    minWidth: 150,
  },
  featuresSection: {
    backgroundColor: '#F0F9FF',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#0EA5E9',
  },
  featuresTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0369A1',
    marginBottom: 12,
  },
  featureItem: {
    flexDirection: 'row',
    marginBottom: 8,
    alignItems: 'flex-start',
  },
  featureBullet: {
    fontSize: 16,
    color: '#0EA5E9',
    marginRight: 10,
    fontWeight: 'bold',
  },
  featureText: {
    flex: 1,
    fontSize: 14,
    color: '#075985',
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
    marginBottom: 15,
  },
  resultStats: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingTop: 15,
    borderTopWidth: 1,
    borderTopColor: '#A5D6A7',
  },
  statItem: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 12,
    color: '#558B2F',
    marginBottom: 5,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#2E7D32',
  },
});

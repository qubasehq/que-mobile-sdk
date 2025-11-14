import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useAgent, useVoice, VoiceWaveAnimation } from 'que-mobile-sdk';

export default function VoiceCommandScreen() {
  const [apiKey, setApiKey] = useState('');
  const [transcript, setTranscript] = useState('');
  const [spokenText, setSpokenText] = useState('');

  const { execute, isRunning, result, error, stop } = useAgent({
    apiKey: apiKey || 'YOUR_GEMINI_API_KEY',
    maxSteps: 30,
    debugMode: false,
  });

  const { speak, startListening, stopListening, isListening, isSpeaking } = useVoice();

  const handleVoiceCommand = async () => {
    try {
      // Start listening for voice input
      const voiceResult = await startListening();
      setTranscript(voiceResult);

      if (voiceResult && voiceResult.trim()) {
        // Execute the voice command
        await execute(voiceResult);
      } else {
        Alert.alert('No Input', 'No voice input detected. Please try again.');
      }
    } catch (err) {
      console.error('Voice command error:', err);
      Alert.alert('Error', 'Failed to process voice command');
    }
  };

  const handleTestSpeak = async () => {
    const testMessage = 'Hello! I am the QUE Mobile SDK voice assistant. I can help you automate tasks on your device.';
    setSpokenText(testMessage);
    try {
      await speak(testMessage);
    } catch (err) {
      console.error('Speak error:', err);
      Alert.alert('Error', 'Failed to speak text');
    }
  };

  const handleTestListen = async () => {
    try {
      const result = await startListening();
      setTranscript(result);
      Alert.alert('You said:', result || 'Nothing detected');
    } catch (err) {
      console.error('Listen error:', err);
      Alert.alert('Error', 'Failed to listen');
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollView}>
        <View style={styles.content}>
        <Text style={styles.title}>Voice Command Integration</Text>
        <Text style={styles.description}>
          Use voice commands to control your device. Speak naturally and the agent will execute your commands.
        </Text>

        <View style={styles.statusSection}>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Listening:</Text>
            <View style={[styles.statusIndicator, isListening && styles.statusActive]} />
            <Text style={styles.statusText}>{isListening ? 'Active' : 'Inactive'}</Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Speaking:</Text>
            <View style={[styles.statusIndicator, isSpeaking && styles.statusActive]} />
            <Text style={styles.statusText}>{isSpeaking ? 'Active' : 'Inactive'}</Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>Agent Running:</Text>
            <View style={[styles.statusIndicator, isRunning && styles.statusActive]} />
            <Text style={styles.statusText}>{isRunning ? 'Active' : 'Inactive'}</Text>
          </View>
        </View>

        {transcript && (
          <View style={styles.transcriptContainer}>
            <Text style={styles.transcriptLabel}>Last Transcript:</Text>
            <Text style={styles.transcriptText}>{transcript}</Text>
          </View>
        )}

        {spokenText && (
          <View style={styles.spokenContainer}>
            <Text style={styles.spokenLabel}>Last Spoken:</Text>
            <Text style={styles.spokenText}>{spokenText}</Text>
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Voice Testing</Text>
          <TouchableOpacity
            style={styles.testButton}
            onPress={handleTestSpeak}
            disabled={isSpeaking}
          >
            <Text style={styles.buttonText}>
              {isSpeaking ? 'Speaking...' : '🔊 Test Text-to-Speech'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.testButton}
            onPress={handleTestListen}
            disabled={isListening}
          >
            <Text style={styles.buttonText}>
              {isListening ? 'Listening...' : '🎤 Test Speech-to-Text'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Voice Command Execution</Text>
          <Text style={styles.sectionDescription}>
            Tap the button below and speak your command. The agent will listen, transcribe, and execute your request.
          </Text>

          {!isRunning ? (
            <TouchableOpacity
              style={styles.voiceButton}
              onPress={handleVoiceCommand}
              disabled={isListening}
            >
              <Text style={styles.voiceButtonText}>
                {isListening ? '🎤 Listening...' : '🎤 Start Voice Command'}
              </Text>
            </TouchableOpacity>
          ) : (
            <View style={styles.runningContainer}>
              <ActivityIndicator size="large" color="#007AFF" />
              <Text style={styles.runningText}>Executing command...</Text>
              <TouchableOpacity
                style={styles.stopButton}
                onPress={stop}
              >
                <Text style={styles.buttonText}>Stop</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>

        <View style={styles.examplesSection}>
          <Text style={styles.examplesTitle}>Example Voice Commands:</Text>
          <Text style={styles.exampleItem}>• "Open Settings"</Text>
          <Text style={styles.exampleItem}>• "Go to home screen"</Text>
          <Text style={styles.exampleItem}>• "Open Chrome and search for weather"</Text>
          <Text style={styles.exampleItem}>• "Turn on WiFi"</Text>
          <Text style={styles.exampleItem}>• "Show me my notifications"</Text>
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

      {/* Voice Wave Animation */}
      <VoiceWaveAnimation
        isActive={isListening || isSpeaking || isRunning}
        amplitude={isListening ? 0.8 : isSpeaking ? 0.6 : 0.3}
      />
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
    paddingBottom: 180, // Extra padding for wave animation
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
  statusSection: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  statusLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    width: 120,
  },
  statusIndicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#ccc',
    marginRight: 8,
  },
  statusActive: {
    backgroundColor: '#4CAF50',
  },
  statusText: {
    fontSize: 14,
    color: '#666',
  },
  transcriptContainer: {
    backgroundColor: '#E3F2FD',
    padding: 15,
    borderRadius: 8,
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#2196F3',
  },
  transcriptLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1976D2',
    marginBottom: 5,
  },
  transcriptText: {
    fontSize: 16,
    color: '#0D47A1',
  },
  spokenContainer: {
    backgroundColor: '#F3E5F5',
    padding: 15,
    borderRadius: 8,
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#9C27B0',
  },
  spokenLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#7B1FA2',
    marginBottom: 5,
  },
  spokenText: {
    fontSize: 14,
    color: '#4A148C',
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 10,
    color: '#333',
  },
  sectionDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 15,
    lineHeight: 20,
  },
  testButton: {
    backgroundColor: '#9C27B0',
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    marginBottom: 10,
  },
  voiceButton: {
    backgroundColor: '#007AFF',
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
  },
  voiceButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
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
  stopButton: {
    backgroundColor: '#FF3B30',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
    minWidth: 120,
  },
  examplesSection: {
    backgroundColor: '#FFF3E0',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FF9800',
  },
  examplesTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 10,
    color: '#E65100',
  },
  exampleItem: {
    fontSize: 14,
    color: '#F57C00',
    marginBottom: 5,
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

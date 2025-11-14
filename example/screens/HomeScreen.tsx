import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  SafeAreaView,
} from 'react-native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

type RootStackParamList = {
  Home: undefined;
  SimpleTask: undefined;
  VoiceCommand: undefined;
  FileOperations: undefined;
  DebugMode: undefined;
  MultiStep: undefined;
};

type HomeScreenProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'Home'>;
};

export default function HomeScreen({ navigation }: HomeScreenProps) {
  const examples = [
    {
      id: 'SimpleTask',
      title: '🎯 Simple Task Execution',
      description: 'Execute basic automation tasks using natural language commands',
      color: '#007AFF',
      screen: 'SimpleTask' as keyof RootStackParamList,
    },
    {
      id: 'VoiceCommand',
      title: '🎤 Voice Commands',
      description: 'Control your device using voice commands with speech-to-text',
      color: '#5856D6',
      screen: 'VoiceCommand' as keyof RootStackParamList,
    },
    {
      id: 'FileOperations',
      title: '📁 File Operations',
      description: 'Demonstrate file system operations and agent-based file management',
      color: '#34C759',
      screen: 'FileOperations' as keyof RootStackParamList,
    },
    {
      id: 'DebugMode',
      title: '🐛 Debug Mode',
      description: 'See real-time debug information and visual feedback during execution',
      color: '#FF9500',
      screen: 'DebugMode' as keyof RootStackParamList,
    },
    {
      id: 'MultiStep',
      title: '🔄 Multi-Step Tasks',
      description: 'Execute complex tasks with multiple steps across different apps',
      color: '#FF3B30',
      screen: 'MultiStep' as keyof RootStackParamList,
    },
  ];

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView}>
        <View style={styles.content}>
          <View style={styles.header}>
            <Text style={styles.title}>QUE Mobile SDK</Text>
            <Text style={styles.subtitle}>
              AI-Powered Android Automation Examples
            </Text>
          </View>

          <View style={styles.infoCard}>
            <Text style={styles.infoTitle}>Welcome! 👋</Text>
            <Text style={styles.infoText}>
              This app demonstrates the capabilities of the QUE Mobile SDK. Each example showcases different features of the AI-powered automation framework.
            </Text>
          </View>

          <Text style={styles.sectionTitle}>Examples</Text>

          {examples.map((example) => (
            <TouchableOpacity
              key={example.id}
              style={[styles.exampleCard, { borderLeftColor: example.color }]}
              onPress={() => navigation.navigate(example.screen)}
              activeOpacity={0.7}
            >
              <View style={styles.exampleContent}>
                <Text style={styles.exampleTitle}>{example.title}</Text>
                <Text style={styles.exampleDescription}>
                  {example.description}
                </Text>
              </View>
              <View style={[styles.exampleArrow, { backgroundColor: example.color }]}>
                <Text style={styles.arrowText}>→</Text>
              </View>
            </TouchableOpacity>
          ))}

          <View style={styles.footer}>
            <Text style={styles.footerTitle}>Getting Started</Text>
            <Text style={styles.footerText}>
              1. Make sure you have enabled accessibility permissions for the app
            </Text>
            <Text style={styles.footerText}>
              2. Add your Gemini API key in each example (or use the default)
            </Text>
            <Text style={styles.footerText}>
              3. Try out different examples to see the SDK in action
            </Text>
          </View>

          <View style={styles.featuresCard}>
            <Text style={styles.featuresTitle}>Key Features</Text>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>🤖</Text>
              <Text style={styles.featureText}>AI-powered task execution</Text>
            </View>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>👁️</Text>
              <Text style={styles.featureText}>Screen perception & understanding</Text>
            </View>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>🎯</Text>
              <Text style={styles.featureText}>Natural language commands</Text>
            </View>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>🔄</Text>
              <Text style={styles.featureText}>Multi-step task automation</Text>
            </View>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>🎤</Text>
              <Text style={styles.featureText}>Voice control integration</Text>
            </View>
            <View style={styles.featureRow}>
              <Text style={styles.featureIcon}>📁</Text>
              <Text style={styles.featureText}>File system operations</Text>
            </View>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
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
  header: {
    marginBottom: 20,
    alignItems: 'center',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
  infoCard: {
    backgroundColor: '#E8F4FD',
    padding: 20,
    borderRadius: 12,
    marginBottom: 25,
    borderWidth: 1,
    borderColor: '#2196F3',
  },
  infoTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1976D2',
    marginBottom: 10,
  },
  infoText: {
    fontSize: 15,
    color: '#0D47A1',
    lineHeight: 22,
  },
  sectionTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 15,
  },
  exampleCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 20,
    marginBottom: 15,
    flexDirection: 'row',
    alignItems: 'center',
    borderLeftWidth: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  exampleContent: {
    flex: 1,
  },
  exampleTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 6,
  },
  exampleDescription: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
  exampleArrow: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 12,
  },
  arrowText: {
    color: '#fff',
    fontSize: 20,
    fontWeight: 'bold',
  },
  footer: {
    backgroundColor: '#FFF3E0',
    padding: 20,
    borderRadius: 12,
    marginTop: 10,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#FF9800',
  },
  footerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#E65100',
    marginBottom: 12,
  },
  footerText: {
    fontSize: 14,
    color: '#F57C00',
    marginBottom: 8,
    lineHeight: 20,
  },
  featuresCard: {
    backgroundColor: '#F0F9FF',
    padding: 20,
    borderRadius: 12,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#0EA5E9',
  },
  featuresTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#0369A1',
    marginBottom: 15,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  featureIcon: {
    fontSize: 24,
    marginRight: 12,
    width: 32,
  },
  featureText: {
    flex: 1,
    fontSize: 15,
    color: '#075985',
  },
});

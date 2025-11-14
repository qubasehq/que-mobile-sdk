import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from 'react-native';
import { useAgent, useFileSystem } from 'que-mobile-sdk';

export default function FileOperationsScreen() {
  const [apiKey, setApiKey] = useState('');
  const [task, setTask] = useState('Create a file called notes.txt with content "Hello World"');
  const [filePath, setFilePath] = useState('');
  const [fileContent, setFileContent] = useState('');
  const [fileList, setFileList] = useState<string[]>([]);

  const { execute, isRunning, result, error } = useAgent({
    apiKey: apiKey || 'YOUR_GEMINI_API_KEY',
    maxSteps: 20,
    debugMode: false,
  });

  const { readFile, writeFile, listFiles, deleteFile, fileExists } = useFileSystem();

  const handleExecuteFileTask = async () => {
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

  const handleReadFile = async () => {
    if (!filePath.trim()) {
      Alert.alert('Error', 'Please enter a file path');
      return;
    }

    try {
      const content = await readFile(filePath);
      setFileContent(content);
      Alert.alert('Success', 'File read successfully');
    } catch (err) {
      Alert.alert('Error', `Failed to read file: ${err}`);
    }
  };

  const handleWriteFile = async () => {
    if (!filePath.trim() || !fileContent.trim()) {
      Alert.alert('Error', 'Please enter both file path and content');
      return;
    }

    try {
      await writeFile(filePath, fileContent);
      Alert.alert('Success', 'File written successfully');
    } catch (err) {
      Alert.alert('Error', `Failed to write file: ${err}`);
    }
  };

  const handleListFiles = async () => {
    try {
      const files = await listFiles();
      setFileList(files);
      Alert.alert('Success', `Found ${files.length} files`);
    } catch (err) {
      Alert.alert('Error', `Failed to list files: ${err}`);
    }
  };

  const handleDeleteFile = async () => {
    if (!filePath.trim()) {
      Alert.alert('Error', 'Please enter a file path');
      return;
    }

    Alert.alert(
      'Confirm Delete',
      `Are you sure you want to delete ${filePath}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteFile(filePath);
              Alert.alert('Success', 'File deleted successfully');
              setFilePath('');
              setFileContent('');
            } catch (err) {
              Alert.alert('Error', `Failed to delete file: ${err}`);
            }
          },
        },
      ]
    );
  };

  const handleCheckExists = async () => {
    if (!filePath.trim()) {
      Alert.alert('Error', 'Please enter a file path');
      return;
    }

    try {
      const exists = await fileExists(filePath);
      Alert.alert(
        'File Status',
        exists ? 'File exists' : 'File does not exist'
      );
    } catch (err) {
      Alert.alert('Error', `Failed to check file: ${err}`);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>File Operations</Text>
        <Text style={styles.description}>
          Demonstrate file system operations using both direct API calls and agent-based automation.
        </Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Agent-Based File Operations</Text>
          <Text style={styles.sectionDescription}>
            Use natural language to perform file operations
          </Text>

          <TextInput
            style={styles.input}
            value={task}
            onChangeText={setTask}
            placeholder="Enter file operation task"
            multiline
            numberOfLines={3}
            editable={!isRunning}
          />

          <View style={styles.examplesSection}>
            <Text style={styles.examplesTitle}>Example Tasks:</Text>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('Create a file called notes.txt with content "Hello World"')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>Create a text file</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('Read the contents of notes.txt')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>Read a file</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.exampleButton}
              onPress={() => setTask('List all files in the app directory')}
              disabled={isRunning}
            >
              <Text style={styles.exampleText}>List all files</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={[styles.executeButton, isRunning && styles.disabledButton]}
            onPress={handleExecuteFileTask}
            disabled={isRunning}
          >
            <Text style={styles.buttonText}>
              {isRunning ? 'Executing...' : 'Execute Task'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Direct File System API</Text>
          <Text style={styles.sectionDescription}>
            Use the file system hooks directly
          </Text>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>File Path</Text>
            <TextInput
              style={styles.input}
              value={filePath}
              onChangeText={setFilePath}
              placeholder="e.g., notes.txt"
              autoCapitalize="none"
            />
          </View>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>File Content</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              value={fileContent}
              onChangeText={setFileContent}
              placeholder="Enter file content"
              multiline
              numberOfLines={4}
            />
          </View>

          <View style={styles.buttonGrid}>
            <TouchableOpacity
              style={styles.apiButton}
              onPress={handleReadFile}
            >
              <Text style={styles.apiButtonText}>📖 Read</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.apiButton}
              onPress={handleWriteFile}
            >
              <Text style={styles.apiButtonText}>✏️ Write</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.apiButton}
              onPress={handleCheckExists}
            >
              <Text style={styles.apiButtonText}>🔍 Check</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.apiButton, styles.deleteButton]}
              onPress={handleDeleteFile}
            >
              <Text style={styles.apiButtonText}>🗑️ Delete</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={styles.listButton}
            onPress={handleListFiles}
          >
            <Text style={styles.buttonText}>📂 List All Files</Text>
          </TouchableOpacity>
        </View>

        {fileList.length > 0 && (
          <View style={styles.fileListContainer}>
            <Text style={styles.fileListTitle}>Files ({fileList.length}):</Text>
            {fileList.map((file, index) => (
              <TouchableOpacity
                key={index}
                style={styles.fileItem}
                onPress={() => setFilePath(file)}
              >
                <Text style={styles.fileItemText}>📄 {file}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

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
            {result.files.length > 0 && (
              <View style={styles.resultFiles}>
                <Text style={styles.resultFilesTitle}>Files accessed:</Text>
                {result.files.map((file, index) => (
                  <Text key={index} style={styles.resultFileItem}>• {file}</Text>
                ))}
              </View>
            )}
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
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
    color: '#333',
  },
  sectionDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 15,
    lineHeight: 20,
  },
  divider: {
    height: 1,
    backgroundColor: '#ddd',
    marginVertical: 20,
  },
  inputGroup: {
    marginBottom: 15,
  },
  label: {
    fontSize: 14,
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
    marginBottom: 15,
    padding: 12,
    backgroundColor: '#fff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  examplesTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
    color: '#666',
  },
  exampleButton: {
    padding: 10,
    backgroundColor: '#f0f0f0',
    borderRadius: 6,
    marginBottom: 6,
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
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginBottom: 15,
  },
  apiButton: {
    backgroundColor: '#34C759',
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    width: '48%',
    marginBottom: 10,
  },
  deleteButton: {
    backgroundColor: '#FF3B30',
  },
  apiButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  listButton: {
    backgroundColor: '#5856D6',
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
  },
  fileListContainer: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  fileListTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 10,
    color: '#333',
  },
  fileItem: {
    padding: 10,
    backgroundColor: '#f9f9f9',
    borderRadius: 6,
    marginBottom: 6,
  },
  fileItemText: {
    fontSize: 14,
    color: '#333',
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
  resultFiles: {
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#A5D6A7',
  },
  resultFilesTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#2E7D32',
    marginBottom: 5,
  },
  resultFileItem: {
    fontSize: 12,
    color: '#558B2F',
    marginBottom: 3,
  },
});

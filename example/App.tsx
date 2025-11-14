import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import HomeScreen from './screens/HomeScreen';
import SimpleTaskScreen from './screens/SimpleTaskScreen';
import VoiceCommandScreen from './screens/VoiceCommandScreen';
import FileOperationsScreen from './screens/FileOperationsScreen';
import DebugModeScreen from './screens/DebugModeScreen';
import MultiStepScreen from './screens/MultiStepScreen';

const Stack = createNativeStackNavigator();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator
        initialRouteName="Home"
        screenOptions={{
          headerStyle: {
            backgroundColor: '#007AFF',
          },
          headerTintColor: '#fff',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        }}
      >
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'QUE Mobile SDK Examples' }}
        />
        <Stack.Screen
          name="SimpleTask"
          component={SimpleTaskScreen}
          options={{ title: 'Simple Task' }}
        />
        <Stack.Screen
          name="VoiceCommand"
          component={VoiceCommandScreen}
          options={{ title: 'Voice Commands' }}
        />
        <Stack.Screen
          name="FileOperations"
          component={FileOperationsScreen}
          options={{ title: 'File Operations' }}
        />
        <Stack.Screen
          name="DebugMode"
          component={DebugModeScreen}
          options={{ title: 'Debug Mode' }}
        />
        <Stack.Screen
          name="MultiStep"
          component={MultiStepScreen}
          options={{ title: 'Multi-Step Tasks' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

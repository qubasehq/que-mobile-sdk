# Que Mobile SDK: Expo Integration Guide

Yes, you **CAN** integrate this into an Expo app, but it requires a **Custom Development Client** (you cannot use "Expo Go").

## 1. The Strategy: "Native Module + Config Plugin"

Since Que SDK relies on `AccessibilityService` (a deep system feature), you must:
1.  **Modify AndroidManifest.xml**: To register the service. (Done via **Config Plugin**)
2.  **Bridge Key Functions**: To start/stop the agent from JS. (Done via **Expo Module**)

## 2. Step-by-Step Implementation

### A. The Config Plugin (`app.plugin.js`)
You already have a basic plugin. It needs to be robust to add the specific `<service>` tag.

```javascript
const { withAndroidManifest, AndroidConfig } = require('@expo/config-plugins');

const withAccessibilityService = (config) => {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults;
    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(androidManifest);

    // Add the Service to Manifest
    mainApplication.service = mainApplication.service || [];
    mainApplication.service.push({
      $: {
        'android:name': 'com.que.platform.android.QueAccessibilityService',
        'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
        'android:exported': 'false', // Security
        'android:label': '@string/accessibility_service_label'
      },
      'intent-filter': [{ action: [{ $: { 'android:name': 'android.accessibilityservice.AccessibilityService' } }] }],
      'meta-data': [{ $: { 'android:name': 'android.accessibilityservice', 'android:resource': '@xml/accessibility_service_config' } }]
    });

    return config;
  });
};

module.exports = withAccessibilityService;
```

### B. The Expo Module (The JS Bridge)
In `que-expo/android/src/main/java/expo/modules/quemobilesdk/QueModule.kt`:

```kotlin
package expo.modules.quemobilesdk

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import com.que.platform.android.QueAccessibilityService
import android.content.Intent

class QueModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("QueMobileSDK")

    // Function to check if service is running
    Function("isServiceEnabled") {
      return@Function QueAccessibilityService.isConnected
    }

    // Function to open Accessibility Settings
    Function("openSettings") {
      val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      appContext.reactContext?.startActivity(intent)
    }
  }
}
```

## 3. Usage in React Native / Expo

```javascript
import * as QueSDK from 'que-mobile-sdk';

export default function App() {
  const checkStatus = () => {
    const isRunning = QueSDK.isServiceEnabled();
    if (!isRunning) {
      alert("Please enable Que Service!");
      QueSDK.openSettings();
    }
  };

  return <Button title="Start Agent" onPress={checkStatus} />;
}
```

## 4. Critical Warning
*   **No Expo Go**: You must run `npx expo run:android`.
*   **Accessibility Service Limitations**: On pure Expo, you cannot easily show the "Cosmic Overlay" (Bubbles) over other apps without complex `SYSTEM_ALERT_WINDOW` handling. The "Headless" B2B strategy is better here.

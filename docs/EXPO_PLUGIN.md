# Expo Config Plugin

The QUE Mobile SDK includes an Expo config plugin that automatically configures your Android app with the necessary permissions and services.

## What the Plugin Does

The plugin automatically:

1. **Adds Required Permissions**:
   - `BIND_ACCESSIBILITY_SERVICE` - Required for UI automation
   - `INTERNET` - Required for LLM API calls (Gemini)
   - `RECORD_AUDIO` - Required for voice features (optional)

2. **Registers Accessibility Service**:
   - Registers `QueAccessibilityService` in AndroidManifest.xml
   - Configures proper intent filters
   - Links to accessibility service configuration XML

## Installation

### 1. Install the Package

```bash
npm install que-mobile-sdk
# or
yarn add que-mobile-sdk
```

### 2. Add Plugin to app.json

Add the plugin to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      "que-mobile-sdk"
    ]
  }
}
```

### 3. Prebuild

Run prebuild to apply the plugin:

```bash
npx expo prebuild
```

This will generate the native Android code with all necessary configurations.

## Manual Configuration (Without Plugin)

If you're not using Expo or prefer manual configuration, you need to:

### 1. Add Permissions to AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- rest of manifest -->
</manifest>
```

### 2. Register Accessibility Service

```xml
<application>
    <service
        android:name="expo.modules.quemobilesdk.QueAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="true"
        android:label="@string/app_name">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```

## Enabling Accessibility Service

After installation, users need to manually enable the accessibility service:

1. Open **Settings** → **Accessibility**
2. Find your app name in the list
3. Toggle the service **ON**
4. Accept the permission dialog

You can guide users to this screen programmatically:

```typescript
import { Linking } from 'react-native';

const openAccessibilitySettings = () => {
  Linking.openSettings();
};
```

## Troubleshooting

### Plugin Not Applied

If the plugin doesn't seem to work:

1. Clean the build:
   ```bash
   npx expo prebuild --clean
   ```

2. Verify the plugin is listed in app.json

3. Check that `@expo/config-plugins` is installed

### Service Not Found

If you get "Service not found" errors:

1. Ensure you ran `npx expo prebuild`
2. Rebuild the app: `npx expo run:android`
3. Check that the service is registered in `android/app/src/main/AndroidManifest.xml`

### Permission Denied

If you get permission errors:

1. Verify the accessibility service is enabled in Settings
2. Check that all permissions are in AndroidManifest.xml
3. For RECORD_AUDIO, you may need to request runtime permission:

```typescript
import { PermissionsAndroid } from 'react-native';

const requestMicPermission = async () => {
  const granted = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
  );
  return granted === PermissionsAndroid.RESULTS.GRANTED;
};
```

## Advanced Configuration

### Custom Service Label

To customize the service label shown in accessibility settings, modify your `strings.xml`:

```xml
<resources>
    <string name="app_name">My AI Assistant</string>
</resources>
```

### Multiple Plugins

The QUE SDK plugin works alongside other Expo plugins:

```json
{
  "expo": {
    "plugins": [
      "que-mobile-sdk",
      "expo-camera",
      "expo-location"
    ]
  }
}
```

## Plugin Source Code

The plugin source code is available at `node_modules/que-mobile-sdk/app.plugin.js` for reference or customization.

## Support

For issues or questions:
- GitHub Issues: https://github.com/qubasehq/que-mobile-sdk/issues
- Documentation: https://github.com/qubasehq/que-mobile-sdk#readme

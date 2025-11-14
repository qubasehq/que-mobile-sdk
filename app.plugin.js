const { withAndroidManifest } = require('@expo/config-plugins');

/**
 * Expo Config Plugin for QUE Mobile SDK
 * 
 * This plugin automatically configures Android permissions and services
 * required for the QUE Mobile SDK to function properly.
 * 
 * Features:
 * - Adds BIND_ACCESSIBILITY_SERVICE permission
 * - Adds INTERNET permission for LLM API calls
 * - Adds RECORD_AUDIO permission for voice features
 * - Registers QueAccessibilityService with proper intent filters
 * - Configures accessibility service metadata
 */

const ACCESSIBILITY_SERVICE_PERMISSION = 'android.permission.BIND_ACCESSIBILITY_SERVICE';
const INTERNET_PERMISSION = 'android.permission.INTERNET';
const RECORD_AUDIO_PERMISSION = 'android.permission.RECORD_AUDIO';

/**
 * Add permissions to AndroidManifest.xml
 */
function addPermissions(androidManifest) {
  const { manifest } = androidManifest;

  // Ensure uses-permission array exists
  if (!manifest['uses-permission']) {
    manifest['uses-permission'] = [];
  }

  const permissions = [
    ACCESSIBILITY_SERVICE_PERMISSION,
    INTERNET_PERMISSION,
    RECORD_AUDIO_PERMISSION
  ];

  // Add each permission if it doesn't already exist
  permissions.forEach(permission => {
    const existingPermission = manifest['uses-permission'].find(
      item => item.$?.['android:name'] === permission
    );

    if (!existingPermission) {
      manifest['uses-permission'].push({
        $: {
          'android:name': permission
        }
      });
    }
  });

  return androidManifest;
}

/**
 * Register QueAccessibilityService in AndroidManifest.xml
 */
function addAccessibilityService(androidManifest) {
  const { manifest } = androidManifest;

  // Ensure application exists
  if (!manifest.application) {
    manifest.application = [{}];
  }

  const application = manifest.application[0];

  // Ensure service array exists
  if (!application.service) {
    application.service = [];
  }

  // Check if service already exists
  const existingService = application.service.find(
    service => service.$?.['android:name'] === 'expo.modules.quemobilesdk.QueAccessibilityService'
  );

  if (!existingService) {
    // Add the accessibility service
    application.service.push({
      $: {
        'android:name': 'expo.modules.quemobilesdk.QueAccessibilityService',
        'android:permission': ACCESSIBILITY_SERVICE_PERMISSION,
        'android:exported': 'true',
        'android:label': '@string/app_name'
      },
      'intent-filter': [
        {
          action: [
            {
              $: {
                'android:name': 'android.accessibilityservice.AccessibilityService'
              }
            }
          ]
        }
      ],
      'meta-data': [
        {
          $: {
            'android:name': 'android.accessibilityservice',
            'android:resource': '@xml/accessibility_service_config'
          }
        }
      ]
    });
  }

  return androidManifest;
}

/**
 * Main plugin function
 */
const withQueSDK = (config) => {
  return withAndroidManifest(config, async (config) => {
    let androidManifest = config.modResults;

    // Add required permissions
    androidManifest = addPermissions(androidManifest);

    // Register accessibility service
    androidManifest = addAccessibilityService(androidManifest);

    config.modResults = androidManifest;
    return config;
  });
};

module.exports = withQueSDK;

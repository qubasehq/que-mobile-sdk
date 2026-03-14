const {
    withPlugins,
    withDangerousMod,
    withProjectBuildGradle,
    withAppBuildGradle,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

// ──────────────────────────────────────────────
// 1. Add kotlin-serialization classpath + bump minSdkVersion
// ──────────────────────────────────────────────
const withQueBuildGradle = (config) => {
    return withProjectBuildGradle(config, (config) => {
        let contents = config.modResults.contents;

        if (!contents.includes('kotlin-serialization')) {
            contents = contents.replace(
                "classpath('org.jetbrains.kotlin:kotlin-gradle-plugin')",
                "classpath('org.jetbrains.kotlin:kotlin-gradle-plugin')\n    classpath('org.jetbrains.kotlin:kotlin-serialization')"
            );
        }

        config.modResults.contents = contents;
        return config;
    });
};

const withQueMinSdk = (config) => {
    return withAppBuildGradle(config, (config) => {
        let contents = config.modResults.contents;

        // Replace minSdkVersion reference with hardcoded 26
        contents = contents.replace(
            /minSdkVersion\s+rootProject\.ext\.minSdkVersion/,
            'minSdkVersion 26'
        );

        config.modResults.contents = contents;
        return config;
    });
};

// ──────────────────────────────────────────────
// 2. Generate XML resources that the SDK AndroidManifest references
//    (que-platform-android manifest references @xml/accessibility_service_config)
// ──────────────────────────────────────────────
const withQueResources = (config) => {
    return withDangerousMod(config, [
        'android',
        async (config) => {
            const resDir = path.join(
                config.modRequest.platformProjectRoot,
                'app', 'src', 'main', 'res'
            );

            // Create xml/ dir and write accessibility service config
            const xmlDir = path.join(resDir, 'xml');
            fs.mkdirSync(xmlDir, { recursive: true });

            const configXml = `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description"
    android:settingsActivity=".MainActivity" />
`;
            fs.writeFileSync(
                path.join(xmlDir, 'accessibility_service_config.xml'),
                configXml
            );

            // Create strings resource for accessibility service description
            const valuesDir = path.join(resDir, 'values');
            fs.mkdirSync(valuesDir, { recursive: true });

            const stringsFile = path.join(valuesDir, 'que_strings.xml');
            if (!fs.existsSync(stringsFile)) {
                const stringsXml = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="accessibility_service_description">QUE Mobile Agent needs accessibility access to read screen content, perform gestures, and automate tasks on your behalf.</string>
</resources>
`;
                fs.writeFileSync(stringsFile, stringsXml);
            }

            return config;
        },
    ]);
};

// ──────────────────────────────────────────────
// 3. Include SDK modules in settings.gradle
// ──────────────────────────────────────────────
const withQueSettingsGradle = (config) => {
    return withDangerousMod(config, [
        'android',
        async (config) => {
            const settingsGradlePath = path.join(
                config.modRequest.platformProjectRoot,
                'settings.gradle'
            );

            if (!fs.existsSync(settingsGradlePath)) {
                return config;
            }

            let settingsContent = fs.readFileSync(settingsGradlePath, 'utf-8');

            if (settingsContent.includes(':que-core')) {
                return config;
            }

            const queExpoDir = path.resolve(__dirname);
            const sdkRoot = path.resolve(queExpoDir, '..').replace(/\\/g, '/');

            const sdkModules = [
                'que-core',
                'que-platform-android',
                'que-actions',
                'que-llm',
                'que-vision',
            ];

            let includeBlock = '\n// QUE Mobile SDK modules\n';
            for (const mod of sdkModules) {
                const modPath = path.resolve(sdkRoot, mod).replace(/\\/g, '/');
                includeBlock += `include ':${mod}'\n`;
                includeBlock += `project(':${mod}').projectDir = new File('${modPath}')\n`;
            }

            settingsContent += includeBlock;
            fs.writeFileSync(settingsGradlePath, settingsContent);

            return config;
        },
    ]);
};

// ──────────────────────────────────────────────
// Main Plugin
// ──────────────────────────────────────────────
const withQueMobileSDK = (config, _props) => {
    return withPlugins(config, [
        withQueMinSdk,
        withQueBuildGradle,
        withQueResources,
        withQueSettingsGradle,
    ]);
};

module.exports = withQueMobileSDK;

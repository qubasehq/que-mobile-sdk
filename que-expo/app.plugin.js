const { withPlugins, AndroidConfig } = require('@expo/config-plugins');

const withQuePermissions = (config) => {
    return AndroidConfig.Permissions.withPermissions(config, [
        'android.permission.BIND_ACCESSIBILITY_SERVICE',
        'android.permission.SYSTEM_ALERT_WINDOW',
        'android.permission.FOREGROUND_SERVICE',
        'android.permission.INTERNET'
    ]);
};

const withQueMobileSDK = (config) => {
    return withPlugins(config, [
        withQuePermissions,
    ]);
};

module.exports = withQueMobileSDK;

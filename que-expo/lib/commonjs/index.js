"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;
var _reactNative = require("react-native");
const {
  QueMobileSDK
} = _reactNative.NativeModules;
if (!QueMobileSDK) {
  throw new Error('QueMobileSDK native module is not available. Make sure you have run `npx expo prebuild` and the native code is properly linked.');
}
const eventEmitter = new _reactNative.NativeEventEmitter(QueMobileSDK);
const stateListeners = [];

// Subscribe to state changes from native module
eventEmitter.addListener('onStateChange', state => {
  stateListeners.forEach(listener => listener(state));
});
var _default = exports.default = {
  /**
   * Check if all required permissions are granted
   */
  async hasRequiredPermissions() {
    return await QueMobileSDK.hasRequiredPermissions();
  },
  /**
   * Request required permissions (accessibility and overlay)
   */
  async requestPermissions() {
    return await QueMobileSDK.requestPermissions();
  },
  /**
   * Set the Gemini API key
   */
  async setApiKey(apiKey) {
    return await QueMobileSDK.setApiKey(apiKey);
  },
  /**
   * Start the agent with a task
   */
  async startAgent(task) {
    return await QueMobileSDK.startAgent(task);
  },
  /**
   * Stop the running agent
   */
  async stopAgent() {
    return await QueMobileSDK.stopAgent();
  },
  /**
   * Add a listener for agent state changes
   */
  addStateListener(listener) {
    stateListeners.push(listener);
  },
  /**
   * Remove a state listener
   */
  removeStateListener(listener) {
    const index = stateListeners.indexOf(listener);
    if (index > -1) {
      stateListeners.splice(index, 1);
    }
  }
};
//# sourceMappingURL=index.js.map
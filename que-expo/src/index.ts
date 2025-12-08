import { NativeModules, NativeEventEmitter } from 'react-native';

const { QueMobileSDK } = NativeModules;

if (!QueMobileSDK) {
    throw new Error(
        'QueMobileSDK native module is not available. Make sure you have run `npx expo prebuild` and the native code is properly linked.'
    );
}

const eventEmitter = new NativeEventEmitter(QueMobileSDK);

type StateListener = (state: string) => void;

const stateListeners: StateListener[] = [];

// Subscribe to state changes from native module
eventEmitter.addListener('onStateChange', (state: string) => {
    stateListeners.forEach(listener => listener(state));
});

export default {
    /**
     * Check if all required permissions are granted
     */
    async hasRequiredPermissions(): Promise<boolean> {
        return await QueMobileSDK.hasRequiredPermissions();
    },

    /**
     * Request required permissions (accessibility and overlay)
     */
    async requestPermissions(): Promise<void> {
        return await QueMobileSDK.requestPermissions();
    },

    /**
     * Set the Gemini API key
     */
    async setApiKey(apiKey: string): Promise<void> {
        return await QueMobileSDK.setApiKey(apiKey);
    },

    /**
     * Start the agent with a task
     * @param task The task description
     * @param maxSteps Maximum number of steps (default: 30)
     */
    async startAgent(task: string, maxSteps: number = 30): Promise<string> {
        return await QueMobileSDK.startAgent(task, maxSteps);
    },

    /**
     * Stop the running agent
     */
    async stopAgent(): Promise<void> {
        return await QueMobileSDK.stopAgent();
    },

    /**
     * Add a listener for agent state changes
     */
    addStateListener(listener: StateListener): void {
        stateListeners.push(listener);
    },

    /**
     * Remove a state listener
     */
    removeStateListener(listener: StateListener): void {
        const index = stateListeners.indexOf(listener);
        if (index > -1) {
            stateListeners.splice(index, 1);
        }
    },
};

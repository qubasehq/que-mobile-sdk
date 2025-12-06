type StateListener = (state: string) => void;
declare const _default: {
    /**
     * Check if all required permissions are granted
     */
    hasRequiredPermissions(): Promise<boolean>;
    /**
     * Request required permissions (accessibility and overlay)
     */
    requestPermissions(): Promise<void>;
    /**
     * Set the Gemini API key
     */
    setApiKey(apiKey: string): Promise<void>;
    /**
     * Start the agent with a task
     */
    startAgent(task: string): Promise<string>;
    /**
     * Stop the running agent
     */
    stopAgent(): Promise<void>;
    /**
     * Add a listener for agent state changes
     */
    addStateListener(listener: StateListener): void;
    /**
     * Remove a state listener
     */
    removeStateListener(listener: StateListener): void;
};
export default _default;
//# sourceMappingURL=index.d.ts.map
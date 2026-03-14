import { requireNativeModule, EventEmitter, type Subscription } from 'expo-modules-core';

// ─── Types ───────────────────────────────────────

export type AgentStateName =
    | 'Idle'
    | 'Perceiving'
    | 'Thinking'
    | 'Acting'
    | 'Finished'
    | 'Error'
    | 'Started'
    | 'Stopped'
    | 'Paused';

export interface AgentStateEvent {
    state: AgentStateName;
    message?: string;
}

export interface AgentStatus {
    state: AgentStateName;
    isRunning: boolean;
}

// ─── Native Module ───────────────────────────────

const QueMobileSDK = requireNativeModule('QueMobileSDK');
const emitter = new EventEmitter(QueMobileSDK);

// ─── Permissions ─────────────────────────────────

/** Check if the accessibility service is enabled */
export function hasAccessibilityPermission(): boolean {
    return QueMobileSDK.hasAccessibilityPermission();
}

/** Check if the overlay (draw-over-apps) permission is granted */
export function hasOverlayPermission(): boolean {
    return QueMobileSDK.hasOverlayPermission();
}

/** Check if all required permissions (accessibility + overlay) are granted */
export function hasRequiredPermissions(): boolean {
    return QueMobileSDK.hasRequiredPermissions();
}

/** Open the accessibility settings screen */
export async function requestAccessibilityPermission(): Promise<void> {
    return QueMobileSDK.requestAccessibilityPermission();
}

/** Open the overlay permission settings screen */
export async function requestOverlayPermission(): Promise<void> {
    return QueMobileSDK.requestOverlayPermission();
}

/** Open the accessibility settings screen (shorthand) */
export async function requestPermissions(): Promise<void> {
    return QueMobileSDK.requestPermissions();
}

/** Open accessibility settings */
export async function openAccessibilitySettings(): Promise<void> {
    return QueMobileSDK.openAccessibilitySettings();
}

/** Open overlay permission settings */
export async function openOverlaySettings(): Promise<void> {
    return QueMobileSDK.openOverlaySettings();
}

// ─── API Key ─────────────────────────────────────

/** Set the Gemini API key (stored in memory only) */
export function setApiKey(apiKey: string): void {
    QueMobileSDK.setApiKey(apiKey);
}

// ─── Agent Control ───────────────────────────────

/** Start the AI agent with a task and optional max steps */
export async function startAgent(task: string, maxSteps: number = 30): Promise<void> {
    return QueMobileSDK.startAgent(task, maxSteps);
}

/** Stop the running agent */
export async function stopAgent(): Promise<void> {
    return QueMobileSDK.stopAgent();
}

/** Pause the running agent */
export async function pauseAgent(): Promise<void> {
    return QueMobileSDK.pauseAgent();
}

/** Resume a paused agent */
export async function resumeAgent(): Promise<void> {
    return QueMobileSDK.resumeAgent();
}

/** Get the current agent state (polling) */
export function getAgentState(): AgentStatus {
    return QueMobileSDK.getAgentState();
}

// ─── Events ──────────────────────────────────────

/** Subscribe to agent state change events */
export function addStateListener(
    listener: (event: AgentStateEvent) => void
): Subscription {
    return emitter.addListener('onAgentStateChange', listener);
}

// ─── Default Export (convenience) ────────────────

const QueSDK = {
    // Permissions
    hasAccessibilityPermission,
    hasOverlayPermission,
    hasRequiredPermissions,
    requestAccessibilityPermission,
    requestOverlayPermission,
    requestPermissions,
    openAccessibilitySettings,
    openOverlaySettings,

    // API Key
    setApiKey,

    // Agent Control
    startAgent,
    stopAgent,
    pauseAgent,
    resumeAgent,
    getAgentState,

    // Events
    addStateListener,
};

export default QueSDK;

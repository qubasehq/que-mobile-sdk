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
    | 'Paused'
    | 'WaitingForUser';

export interface AgentStateEvent {
    state: AgentStateName;
    message?: string;
}

export interface AgentStatus {
    state: AgentStateName;
    isRunning: boolean;
}

/** Emitted when the agent needs to ask the user a question */
export interface UserQuestionEvent {
    question: string;
    options?: string[];
}

/** Emitted when the agent narrates what it's doing */
export interface NarrationEvent {
    message: string;
    type: 'progress' | 'found' | 'warning' | 'done';
}

/** Emitted when the agent needs user confirmation before an irreversible action */
export interface ConfirmationEvent {
    summary: string;
    actionPreview: string;
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
export async function startAgent(task: string, maxSteps: number = 30, model: string): Promise<void> {
    return QueMobileSDK.startAgent(task, maxSteps, model);
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

// ─── Bidirectional Communication ─────────────────

/**
 * Toggle the agent's text-to-speech voice feedback.
 */
export async function setVoiceEnabled(enabled: boolean): Promise<void> {
    return QueMobileSDK.setVoiceEnabled(enabled);
}

/**
 * Reply to the agent when it's waiting for user input.
 * Use after receiving an onUserQuestion or onConfirmationRequired event.
 * For confirmations, reply with "yes"/"no"/"confirm"/"deny".
 */
export async function replyToAgent(reply: string): Promise<void> {
    return QueMobileSDK.replyToAgent(reply);
}

// ─── Events ──────────────────────────────────────

/** Subscribe to agent state change events */
export function addStateListener(
    listener: (event: AgentStateEvent) => void
): Subscription {
    return emitter.addListener('onAgentStateChange', listener);
}

/** Subscribe to agent questions (when agent uses ask_user action) */
export function addUserQuestionListener(
    listener: (event: UserQuestionEvent) => void
): Subscription {
    return emitter.addListener('onUserQuestion', listener);
}

/** Subscribe to agent narrations (real-time progress updates) */
export function addNarrationListener(
    listener: (event: NarrationEvent) => void
): Subscription {
    return emitter.addListener('onNarration', listener);
}

/** Subscribe to confirmation requests (before irreversible actions) */
export function addConfirmationListener(
    listener: (event: ConfirmationEvent) => void
): Subscription {
    return emitter.addListener('onConfirmationRequired', listener);
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

    // Bidirectional Communication
    replyToAgent,

    // Events
    addStateListener,
    addUserQuestionListener,
    addNarrationListener,
    addConfirmationListener,
};

export default QueSDK;

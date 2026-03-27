// @ts-ignore
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

export interface AdvancedSettings {
    enablePredictivePlanning?: boolean;
    enableAdaptiveLearning?: boolean;
    retryFailedActions?: boolean;
    maxRetries?: number;
    maxFailures?: number;
    llmTimeoutMs?: number;
    includeScreenshots?: boolean;
    enableLogging?: boolean;
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

export interface VoiceVolumeEvent {
    volume: number;
}

export interface VoiceTranscriptEvent {
    text: string;
}

export interface VoiceErrorEvent {
    error: string;
}

export interface TaskRecord {
    id: number;
    taskText: string;
    status: string;
    startedAt: number;
    completedAt: number;
    durationSeconds: number;
    summary: string;
    errorReason: string;
    appsTouched: string;
    tokenCount: number;
    stepCount: number;
}

export interface ActionItem {
    id: number;
    taskId: number;
    timestamp: number;
    description: string;
    actionType: string;
    appName: string;
    success: boolean;
}

export interface LocalModelInfo {
    id: string;
    name: string;
    sizeBytes: number;
    description: string;
    parameterCount: string;
    quantization: string;
}

export interface ModelInfo {
    name: string;
    displayName: string;
    description: string;
    supportedMethods: string[];
}

// ─── Native Module ───────────────────────────────

const NativeModule = requireNativeModule('QueExpoV3');
const emitter = new EventEmitter(NativeModule);

// ─── Permissions ─────────────────────────────────

/** Check if the accessibility service is enabled */
export function hasAccessibilityPermission(): boolean {
    return NativeModule.hasAccessibilityPermission();
}

/** Check if the overlay (draw-over-apps) permission is granted */
export function hasOverlayPermission(): boolean {
    return NativeModule.hasOverlayPermission();
}

export function hasAudioPermission(): boolean {
    return NativeModule.hasAudioPermission();
}

/** Check if all required permissions (accessibility + overlay) are granted */
export function hasRequiredPermissions(): boolean {
    return NativeModule.hasRequiredPermissions();
}

/** Open the accessibility settings screen */
export async function requestAccessibilityPermission(): Promise<void> {
    return NativeModule.requestAccessibilityPermission();
}

/** Open the overlay permission settings screen */
export async function requestOverlayPermission(): Promise<void> {
    return NativeModule.requestOverlayPermission();
}

export async function requestAudioPermission(): Promise<void> {
    return NativeModule.requestAudioPermission();
}

/** Open the accessibility settings screen (shorthand) */
export async function requestPermissions(): Promise<void> {
    return NativeModule.requestPermissions();
}

/** Open accessibility settings */
export async function openAccessibilitySettings(): Promise<void> {
    return NativeModule.openAccessibilitySettings();
}

/** Open overlay permission settings */
export async function openOverlaySettings(): Promise<void> {
    return NativeModule.openOverlaySettings();
}

// ─── API Key ─────────────────────────────────────

/** Set the Gemini API key (stored in memory only) */
export function setApiKey(apiKey: string): void {
    NativeModule.setApiKey(apiKey);
}

// ─── Agent Control ───────────────────────────────

/** Start the AI agent with a task and optional max steps */
export async function startAgent(task: string, maxSteps: number = 30, model: string): Promise<void> {
    return NativeModule.startAgent(task, maxSteps, model);
}

/** Stop the running agent */
export async function stopAgent(): Promise<void> {
    return NativeModule.stopAgent();
}

/** Pause the running agent */
export async function pauseAgent(): Promise<void> {
    return NativeModule.pauseAgent();
}

/** Resume a paused agent */
export async function resumeAgent(): Promise<void> {
    return NativeModule.resumeAgent();
}

/** Get the current agent state (polling) */
export function getAgentState(): AgentStatus {
    return NativeModule.getAgentState();
}

// ─── Bidirectional Communication ─────────────────

/**
 * Toggle the agent's text-to-speech voice feedback.
 */
export async function setVoiceEnabled(enabled: boolean): Promise<void> {
    return NativeModule.setVoiceEnabled(enabled);
}

/**
 * Toggle whether the agent can execute actions autonomously.
 */
export async function setAutonomousMode(enabled: boolean): Promise<void> {
    return NativeModule.setAutonomousMode(enabled);
}

/**
 * Configure advanced internal agent settings.
 */
export async function setAdvancedSettings(config: AdvancedSettings): Promise<void> {
    try {
        if (NativeModule?.setNativeAdvancedSettings) {
            console.log('--- Calling NativeModule.setNativeAdvancedSettings ---');
            return await NativeModule.setNativeAdvancedSettings(config);
        }
        console.warn('NativeModule.setNativeAdvancedSettings is not available in this build.');
    } catch (e) {
        console.error('CRASH in setAdvancedSettings:', e);
    }
}

/**
 * Reply to the agent when it's waiting for user input.
 * Use after receiving an onUserQuestion or onConfirmationRequired event.
 * For confirmations, reply with "yes"/"no"/"confirm"/"deny".
 */
export async function replyToAgent(reply: string): Promise<void> {
    return NativeModule.replyToAgent(reply);
}

/**
 * Start native voice recognition (STT).
 * Events will be emitted for volume, partials, and final results.
 */
export async function startVoiceRecognition(): Promise<void> {
    return NativeModule.startVoiceRecognition();
}

// ─── TTS ─────────────────────────────────────────

/** Speak text using native TTS. Resolves when speech finishes. */
export async function speak(text: string): Promise<void> {
    return NativeModule.speak(text);
}

/** Stop any current TTS speech */
export function stopSpeaking(): void {
    NativeModule.stopSpeaking();
}

/** Check if TTS is currently speaking */
export function isSpeaking(): boolean {
    return NativeModule.isSpeaking();
}

// ─── Assistant ───────────────────────────────────

/** Check if Que is set as the default digital assistant */
export function isDefaultAssistant(): boolean {
    return NativeModule.isDefaultAssistant();
}

/** Open the system settings to set default digital assistant */
export async function openAssistantSettings(): Promise<void> {
    return NativeModule.openAssistantSettings();
}

// ─── Memory & Context ────────────────────────────

export function getTaskHistory(limit: number = 50): TaskRecord[] {
    return NativeModule.getTaskHistory(limit);
}

export function getTaskActions(taskId: number): ActionItem[] {
    return NativeModule.getTaskActions(taskId);
}

export function clearHistory(): void {
    NativeModule.clearHistory();
}

export function resolveContext(fields: string[]): Record<string, string> {
    return NativeModule.resolveContext(fields);
}

// ─── Local Model Management ──────────────────────

export function listCloudModels(): Promise<ModelInfo[]> {
  return NativeModule.listCloudModels();
}

export function getAvailableModels(): Promise<LocalModelInfo[]> {
    return NativeModule.getAvailableModels();
}

export function getDownloadedModels(): LocalModelInfo[] {
    return NativeModule.getDownloadedModels();
}

export async function downloadModel(modelId: string): Promise<void> {
    return NativeModule.downloadModel(modelId);
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

/** Subscribe to TTS speaking done events */
export function addSpeakingDoneListener(
    listener: (event: { status: string }) => void
): Subscription {
    return emitter.addListener('onSpeakingDone', listener);
}

/** Subscribe to assistant activation events (power button long press) */
export function addAssistActivatedListener(
    listener: () => void
): Subscription {
    return emitter.addListener('onAssistActivated', listener);
}

export function addVoiceVolumeListener(
    listener: (event: VoiceVolumeEvent) => void
): Subscription {
    return emitter.addListener('onVoiceVolumeChanged', listener);
}

export function addVoicePartialListener(
    listener: (event: VoiceTranscriptEvent) => void
): Subscription {
    return emitter.addListener('onVoicePartialTranscript', listener);
}

export function addVoiceFinalListener(
    listener: (event: VoiceTranscriptEvent) => void
): Subscription {
    return emitter.addListener('onVoiceFinalTranscript', listener);
}

export function addVoiceErrorListener(
    listener: (event: VoiceErrorEvent) => void
): Subscription {
    return emitter.addListener('onVoiceError', listener);
}

// ─── Default Export (convenience) ────────────────

const QueSDK = {
    // Permissions
    hasAccessibilityPermission,
    hasOverlayPermission,
    hasAudioPermission,
    hasRequiredPermissions,
    requestAccessibilityPermission,
    requestOverlayPermission,
    requestAudioPermission,
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
    setVoiceEnabled,
    setAutonomousMode,
    setAdvancedSettings,
    replyToAgent,
    startVoiceRecognition,

    // TTS
    speak,
    stopSpeaking,
    isSpeaking,

    // Assistant
    isDefaultAssistant,
    openAssistantSettings,

    // Memory & Context
    getTaskHistory,
    getTaskActions,
    clearHistory,
    resolveContext,

    // Local Models
    listCloudModels,
    getAvailableModels,
    getDownloadedModels,
    downloadModel,

    // Events
    addStateListener,
    addUserQuestionListener,
    addNarrationListener,
    addConfirmationListener,
    addSpeakingDoneListener,
    addAssistActivatedListener,
    addVoiceVolumeListener,
    addVoicePartialListener,
    addVoiceFinalListener,
    addVoiceErrorListener,
};

export default QueSDK;

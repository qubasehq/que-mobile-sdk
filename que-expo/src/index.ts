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

export interface UserQuestionEvent {
    question: string;
    options?: string[];
}

export interface NarrationEvent {
    message: string;
    type: 'progress' | 'found' | 'warning' | 'done';
}

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

// ─── Assistant Types ─────────────────────────────

export type AssistantMode = 'CHAT' | 'ASSISTANT';

export type AssistantEventType =
    | 'onAssistantThinkingStarted'
    | 'onAssistantThinkingEnded'
    | 'onAssistantListeningStarted'
    | 'onAssistantListeningEnded'
    | 'onAssistantSpeakResponse'
    | 'onAssistantAgentStarted'
    | 'onAssistantAgentNarration'
    | 'onAssistantAgentQuestion'
    | 'onAssistantAgentConfirmation'
    | 'onAssistantAgentFinished'
    | 'onAssistantAgentFailed'
    | 'onAssistantError'
    | 'onAvailableVoices';

export interface AssistantEventData {
    eventType: AssistantEventType;
    data: {
        text?: string;
        speak?: boolean;
        transcript?: string;
        taskDescription?: string;
        message?: string;
        type?: string;
        question?: string;
        options?: string[];
        summary?: string;
        preview?: string;
        reason?: string;
        voices?: any[];
    };
}

export interface AssistantStatus {
    isRunning: boolean;
    mode: string;
}

// ─── Native Module ───────────────────────────────

const NativeModule = requireNativeModule('QueExpoV3');
const emitter = new EventEmitter(NativeModule);

// ─── Native Log Bridge ───────────────────────────
emitter.addListener('onConsoleLog', (event: { message: string }) => {
    console.log(`>>>> [NATIVE_LOG] ${event.message}`);
});

// ─── Permissions ─────────────────────────────────

export function hasAccessibilityPermission(): boolean {
    return NativeModule.hasAccessibilityPermission();
}

export function hasOverlayPermission(): boolean {
    return NativeModule.hasOverlayPermission();
}

export function hasAudioPermission(): boolean {
    return NativeModule.hasAudioPermission();
}

export function hasRequiredPermissions(): boolean {
    return NativeModule.hasRequiredPermissions();
}

export async function requestAccessibilityPermission(): Promise<void> {
    return NativeModule.requestAccessibilityPermission();
}

export async function requestOverlayPermission(): Promise<void> {
    return NativeModule.requestOverlayPermission();
}

export async function requestAudioPermission(): Promise<void> {
    return NativeModule.requestAudioPermission();
}

export async function requestPermissions(): Promise<void> {
    return NativeModule.requestPermissions();
}

export async function openAccessibilitySettings(): Promise<void> {
    return NativeModule.openAccessibilitySettings();
}

export async function openOverlaySettings(): Promise<void> {
    return NativeModule.openOverlaySettings();
}

// ─── API Key ─────────────────────────────────────

export function setApiKey(apiKey: string): void {
    NativeModule.setApiKey(apiKey);
}

// ─── Agent Control ───────────────────────────────

export async function startAgent(task: string, maxSteps: number = 30, model: string): Promise<void> {
    return NativeModule.startAgent(task, maxSteps, model);
}

export async function stopAgent(): Promise<void> {
    return NativeModule.stopAgent();
}

export async function pauseAgent(): Promise<void> {
    return NativeModule.pauseAgent();
}

export async function resumeAgent(): Promise<void> {
    return NativeModule.resumeAgent();
}

export function getAgentState(): AgentStatus {
    return NativeModule.getAgentState();
}

// ─── Bidirectional Communication ─────────────────

export async function setVoiceEnabled(enabled: boolean): Promise<void> {
    return NativeModule.setVoiceEnabled(enabled);
}

export async function setAutonomousMode(enabled: boolean): Promise<void> {
    return NativeModule.setAutonomousMode(enabled);
}

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

export async function replyToAgent(reply: string): Promise<void> {
    return NativeModule.replyToAgent(reply);
}

export async function startVoiceRecognition(): Promise<void> {
    return NativeModule.startVoiceRecognition();
}

// ─── TTS ─────────────────────────────────────────

export async function speak(text: string): Promise<void> {
    return NativeModule.speak(text);
}

export function stopSpeaking(): void {
    NativeModule.stopSpeaking();
}

export function isSpeaking(): boolean {
    return NativeModule.isSpeaking();
}

export function getAvailableVoices(): any[] {
    return NativeModule.getAvailableVoices();
}

// ─── Assistant Activation ────────────────────────

export function isDefaultAssistant(): boolean {
    return NativeModule.isDefaultAssistant();
}

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

// ─── QueAssistant (CHAT / ASSISTANT mode) ────────

/**
 * Start the QueAssistant service.
 * CHAT mode: pure conversation, no automation.
 * ASSISTANT mode: conversation + can invoke automation skills via QueAgent.
 */
export async function startAssistant(
    mode: AssistantMode = 'ASSISTANT',
    model: string = 'gemini-2.5-flash',
    voiceName?: string
): Promise<void> {
    return NativeModule.startAssistant(mode, model, voiceName);
}

/** Stop the running QueAssistant service. */
export async function stopAssistant(): Promise<void> {
    return NativeModule.stopAssistant();
}

/**
 * Send a text message to the running assistant.
 * The assistant will classify the intent and either
 * chat back or trigger automation via QueAgent.
 */
export async function sendToAssistant(text: string): Promise<void> {
    return NativeModule.sendToAssistant(text);
}

/**
 * Reply to the assistant when the agent needs user input during automation.
 * Use after receiving an onAssistantAgentQuestion or onAssistantAgentConfirmation event.
 */
export async function replyToAssistant(reply: string): Promise<void> {
    return NativeModule.replyToAssistant(reply);
}

/** Clear the assistant's conversation memory. */
export async function clearAssistantMemory(): Promise<void> {
    return NativeModule.clearAssistantMemory();
}

/** Get the current assistant state (polling). */
export function getAssistantState(): AssistantStatus {
    return NativeModule.getAssistantState();
}

// ─── Events ──────────────────────────────────────

export function addStateListener(
    listener: (event: AgentStateEvent) => void
): Subscription {
    return emitter.addListener('onAgentStateChange', listener);
}

export function addUserQuestionListener(
    listener: (event: UserQuestionEvent) => void
): Subscription {
    return emitter.addListener('onUserQuestion', listener);
}

export function addNarrationListener(
    listener: (event: NarrationEvent) => void
): Subscription {
    return emitter.addListener('onNarration', listener);
}

export function addConfirmationListener(
    listener: (event: ConfirmationEvent) => void
): Subscription {
    return emitter.addListener('onConfirmationRequired', listener);
}

export function addSpeakingDoneListener(
    listener: (event: { status: string }) => void
): Subscription {
    return emitter.addListener('onSpeakingDone', listener);
}

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

export function addVoiceEndListener(
    listener: (event: { status: string }) => void
): Subscription {
    return emitter.addListener('onVoiceEnd', listener);
}

/**
 * Subscribe to all QueAssistant events.
 * Receives a unified event object with eventType and data fields.
 */
export function addAssistantEventListener(
    listener: (event: AssistantEventData) => void
): Subscription {
    return emitter.addListener('onAssistantEvent', listener);
}

export function addWakeWordListener(
    listener: () => void
): Subscription {
    return emitter.addListener('onWakeWordDetected', listener);
}

export async function startWakeWord(accessKey: string): Promise<void> {
    return NativeModule.startWakeWord(accessKey);
}

export async function stopWakeWord(): Promise<void> {
    return NativeModule.stopWakeWord();
}

// ─── Default Export ──────────────────────────────

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
    getAvailableVoices,

    // Assistant Activation
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

    // QueAssistant (CHAT / ASSISTANT mode)
    startAssistant,
    stopAssistant,
    sendToAssistant,
    replyToAssistant,
    clearAssistantMemory,
    getAssistantState,

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
    addVoiceEndListener,
    addAssistantEventListener,
    addWakeWordListener,
    startWakeWord,
    stopWakeWord,
};

export default QueSDK;

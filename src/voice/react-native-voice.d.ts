/**
 * Type definitions for react-native-voice
 * 
 * Since react-native-voice doesn't provide official TypeScript types,
 * we define the minimal interface we need here.
 */

declare module 'react-native-voice' {
  export interface SpeechResultsEvent {
    value?: string[]
  }

  export interface SpeechErrorEvent {
    error?: {
      message?: string
      code?: string
    }
  }

  export interface SpeechStartEvent {}
  export interface SpeechEndEvent {}

  class Voice {
    static onSpeechStart: ((event: SpeechStartEvent) => void) | null
    static onSpeechEnd: ((event: SpeechEndEvent) => void) | null
    static onSpeechResults: ((event: SpeechResultsEvent) => void) | null
    static onSpeechError: ((event: SpeechErrorEvent) => void) | null

    static isAvailable(): Promise<number | boolean>
    static start(locale: string): Promise<void>
    static stop(): Promise<void>
    static cancel(): Promise<void>
    static destroy(): Promise<void>
    static removeAllListeners(): void
  }

  export default Voice
}

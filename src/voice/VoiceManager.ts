/**
 * VoiceManager - Handles text-to-speech and speech-to-text operations
 * 
 * Wraps react-native-tts and @react-native-voice/voice to provide a unified
 * interface for voice interactions with queuing and error handling.
 */

import Tts from 'react-native-tts'
import Voice, { SpeechResultsEvent, SpeechErrorEvent } from '@react-native-voice/voice'
import { VoiceError } from '../utils/errors'

// ============================================================================
// Types
// ============================================================================

interface SpeechQueueItem {
  text: string
  resolve: () => void
  reject: (error: Error) => void
}

// ============================================================================
// VoiceManager Class
// ============================================================================

export class VoiceManager {
  private isSpeaking: boolean = false
  private isListeningActive: boolean = false
  private speechQueue: SpeechQueueItem[] = []
  private currentListenResolve: ((result: string) => void) | null = null
  private currentListenReject: ((error: Error) => void) | null = null
  private isInitialized: boolean = false
  private ttsAvailable: boolean = false
  private sttAvailable: boolean = false

  constructor() {
    this.initialize()
  }

  // ==========================================================================
  // Initialization
  // ==========================================================================

  /**
   * Initialize TTS and STT engines
   */
  private async initialize(): Promise<void> {
    if (this.isInitialized) {
      return
    }

    try {
      // Initialize TTS
      await this.initializeTTS()
      
      // Initialize STT
      await this.initializeSTT()

      this.isInitialized = true
    } catch (error) {
      console.warn('VoiceManager initialization failed:', error)
      // Don't throw - allow graceful degradation
    }
  }

  /**
   * Initialize Text-to-Speech
   */
  private async initializeTTS(): Promise<void> {
    try {
      // Set up TTS event listeners
      Tts.addEventListener('tts-start', () => {
        this.isSpeaking = true
      })

      Tts.addEventListener('tts-finish', () => {
        this.isSpeaking = false
        this.processNextInQueue()
      })

      Tts.addEventListener('tts-cancel', () => {
        this.isSpeaking = false
        this.processNextInQueue()
      })

      // Set default TTS settings
      await Tts.setDefaultLanguage('en-US')
      await Tts.setDefaultRate(0.5) // Normal speed
      await Tts.setDefaultPitch(1.0) // Normal pitch

      this.ttsAvailable = true
    } catch (error) {
      console.warn('TTS initialization failed:', error)
      this.ttsAvailable = false
    }
  }

  /**
   * Initialize Speech-to-Text
   */
  private async initializeSTT(): Promise<void> {
    try {
      // Set up Voice event listeners
      Voice.onSpeechStart = () => {
        this.isListeningActive = true
      }

      Voice.onSpeechEnd = () => {
        this.isListeningActive = false
      }

      Voice.onSpeechResults = (event: SpeechResultsEvent) => {
        if (event.value && event.value.length > 0) {
          const result = event.value[0]
          if (this.currentListenResolve) {
            this.currentListenResolve(result)
            this.currentListenResolve = null
            this.currentListenReject = null
          }
        }
      }

      Voice.onSpeechError = (event: SpeechErrorEvent) => {
        if (this.currentListenReject) {
          this.currentListenReject(
            new VoiceError(
              `Speech recognition error: ${event.error?.message || 'Unknown error'}`,
              'listen',
              { code: event.error?.code }
            )
          )
          this.currentListenReject = null
          this.currentListenResolve = null
        }
        this.isListeningActive = false
      }

      // Check if speech recognition is available
      const available = await Voice.isAvailable()
      this.sttAvailable = Boolean(available)

    } catch (error) {
      console.warn('STT initialization failed:', error)
      this.sttAvailable = false
    }
  }

  // ==========================================================================
  // Public API
  // ==========================================================================

  /**
   * Speak text using TTS with queuing
   * 
   * @param text - Text to speak
   * @returns Promise that resolves when speech completes
   */
  async speak(text: string): Promise<void> {
    if (!this.ttsAvailable) {
      throw new VoiceError('Text-to-speech is not available', 'speak')
    }

    if (!text || text.trim().length === 0) {
      return
    }

    return new Promise((resolve, reject) => {
      this.speechQueue.push({ text, resolve, reject })
      
      // If not currently speaking, start processing queue
      if (!this.isSpeaking) {
        this.processNextInQueue()
      }
    })
  }

  /**
   * Ask a question by speaking it and then listening for response
   * 
   * @param question - Question to ask
   * @returns Promise that resolves with the user's spoken response
   */
  async ask(question: string): Promise<string> {
    if (!this.ttsAvailable || !this.sttAvailable) {
      throw new VoiceError(
        'Voice interaction requires both TTS and STT to be available',
        'speak'
      )
    }

    // Speak the question
    await this.speak(question)

    // Wait a brief moment for the speech to finish
    await new Promise(resolve => setTimeout(resolve, 500))

    // Listen for response
    return this.startListening()
  }

  /**
   * Start listening for speech input
   * 
   * @returns Promise that resolves with recognized text
   */
  async startListening(): Promise<string> {
    if (!this.sttAvailable) {
      throw new VoiceError('Speech-to-text is not available', 'listen')
    }

    if (this.isListeningActive) {
      throw new VoiceError('Already listening', 'listen')
    }

    return new Promise(async (resolve, reject) => {
      this.currentListenResolve = resolve
      this.currentListenReject = reject

      try {
        await Voice.start('en-US')
      } catch (error) {
        this.currentListenResolve = null
        this.currentListenReject = null
        reject(
          new VoiceError(
            `Failed to start listening: ${error instanceof Error ? error.message : 'Unknown error'}`,
            'listen',
            { originalError: error }
          )
        )
      }
    })
  }

  /**
   * Stop listening for speech input
   */
  async stopListening(): Promise<void> {
    if (!this.isListeningActive) {
      return
    }

    try {
      await Voice.stop()
      this.isListeningActive = false
      
      // Clean up pending promises
      if (this.currentListenReject) {
        this.currentListenReject(new VoiceError('Listening stopped by user', 'listen'))
        this.currentListenResolve = null
        this.currentListenReject = null
      }
    } catch (error) {
      console.warn('Error stopping voice recognition:', error)
    }
  }

  /**
   * Check if voice capabilities are available
   * 
   * @returns Object indicating TTS and STT availability
   */
  isAvailable(): { tts: boolean; stt: boolean; both: boolean } {
    return {
      tts: this.ttsAvailable,
      stt: this.sttAvailable,
      both: this.ttsAvailable && this.sttAvailable,
    }
  }

  /**
   * Get current voice state
   */
  getState(): { isSpeaking: boolean; isListening: boolean } {
    return {
      isSpeaking: this.isSpeaking,
      isListening: this.isListeningActive,
    }
  }

  /**
   * Stop all voice operations and clear queue
   */
  async stopAll(): Promise<void> {
    // Stop TTS
    if (this.isSpeaking) {
      try {
        await Tts.stop()
      } catch (error) {
        console.warn('Error stopping TTS:', error)
      }
    }

    // Stop STT
    if (this.isListeningActive) {
      await this.stopListening()
    }

    // Clear queue and reject pending promises
    while (this.speechQueue.length > 0) {
      const item = this.speechQueue.shift()
      if (item) {
        item.reject(new VoiceError('Speech queue cleared', 'speak'))
      }
    }

    this.isSpeaking = false
  }

  /**
   * Set TTS language
   * 
   * @param language - Language code (e.g., 'en-US', 'es-ES')
   */
  async setLanguage(language: string): Promise<void> {
    if (!this.ttsAvailable) {
      throw new VoiceError('TTS not available', 'speak')
    }

    try {
      await Tts.setDefaultLanguage(language)
    } catch (error) {
      throw new VoiceError(
        `Failed to set language: ${error instanceof Error ? error.message : 'Unknown error'}`,
        'speak',
        { language, originalError: error }
      )
    }
  }

  /**
   * Set TTS speech rate
   * 
   * @param rate - Speech rate (0.0 to 1.0, default 0.5)
   */
  async setRate(rate: number): Promise<void> {
    if (!this.ttsAvailable) {
      throw new VoiceError('TTS not available', 'speak')
    }

    try {
      await Tts.setDefaultRate(Math.max(0, Math.min(1, rate)))
    } catch (error) {
      throw new VoiceError(
        `Failed to set rate: ${error instanceof Error ? error.message : 'Unknown error'}`,
        'speak',
        { rate, originalError: error }
      )
    }
  }

  /**
   * Set TTS pitch
   * 
   * @param pitch - Voice pitch (0.5 to 2.0, default 1.0)
   */
  async setPitch(pitch: number): Promise<void> {
    if (!this.ttsAvailable) {
      throw new VoiceError('TTS not available', 'speak')
    }

    try {
      await Tts.setDefaultPitch(Math.max(0.5, Math.min(2.0, pitch)))
    } catch (error) {
      throw new VoiceError(
        `Failed to set pitch: ${error instanceof Error ? error.message : 'Unknown error'}`,
        'speak',
        { pitch, originalError: error }
      )
    }
  }

  /**
   * Clean up resources
   */
  async destroy(): Promise<void> {
    await this.stopAll()

    // Remove TTS listeners
    Tts.removeAllListeners('tts-start')
    Tts.removeAllListeners('tts-finish')
    Tts.removeAllListeners('tts-cancel')

    // Destroy Voice
    try {
      await Voice.destroy()
    } catch (error) {
      console.warn('Error destroying Voice:', error)
    }

    this.isInitialized = false
    this.ttsAvailable = false
    this.sttAvailable = false
  }

  // ==========================================================================
  // Private Methods
  // ==========================================================================

  /**
   * Process next item in speech queue
   */
  private async processNextInQueue(): Promise<void> {
    if (this.speechQueue.length === 0 || this.isSpeaking) {
      return
    }

    const item = this.speechQueue.shift()
    if (!item) {
      return
    }

    try {
      this.isSpeaking = true
      await Tts.speak(item.text)
      // Note: resolve will be called by tts-finish event
    } catch (error) {
      this.isSpeaking = false
      item.reject(
        new VoiceError(
          `Failed to speak: ${error instanceof Error ? error.message : 'Unknown error'}`,
          'speak',
          { text: item.text, originalError: error }
        )
      )
      // Process next item
      this.processNextInQueue()
    }
  }
}

// ============================================================================
// Singleton Instance (optional)
// ============================================================================

let voiceManagerInstance: VoiceManager | null = null

/**
 * Get singleton VoiceManager instance
 */
export function getVoiceManager(): VoiceManager {
  if (!voiceManagerInstance) {
    voiceManagerInstance = new VoiceManager()
  }
  return voiceManagerInstance
}

/**
 * Destroy singleton instance
 */
export async function destroyVoiceManager(): Promise<void> {
  if (voiceManagerInstance) {
    await voiceManagerInstance.destroy()
    voiceManagerInstance = null
  }
}

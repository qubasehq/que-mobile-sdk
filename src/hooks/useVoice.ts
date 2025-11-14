/**
 * useVoice Hook - React hook for voice interactions
 * 
 * Wraps VoiceManager to provide text-to-speech and speech-to-text
 * capabilities with React state management.
 * 
 * Requirements: 8.5
 */

import { useState, useEffect, useRef, useCallback } from 'react'
import { VoiceManager } from '../voice/VoiceManager'

// ============================================================================
// Hook Return Type
// ============================================================================

export interface UseVoiceReturn {
  /** Speak text using TTS */
  speak: (text: string) => Promise<void>
  /** Start listening for speech input */
  startListening: () => Promise<string>
  /** Stop listening for speech input */
  stopListening: () => Promise<void>
  /** Ask a question and get spoken response */
  ask: (question: string) => Promise<string>
  /** Whether currently speaking */
  isSpeaking: boolean
  /** Whether currently listening */
  isListening: boolean
  /** Voice availability status */
  isAvailable: { tts: boolean; stt: boolean; both: boolean }
  /** Set TTS language */
  setLanguage: (language: string) => Promise<void>
  /** Set TTS speech rate (0.0 to 1.0) */
  setRate: (rate: number) => Promise<void>
  /** Set TTS pitch (0.5 to 2.0) */
  setPitch: (pitch: number) => Promise<void>
  /** Stop all voice operations */
  stopAll: () => Promise<void>
}

// ============================================================================
// useVoice Hook
// ============================================================================

/**
 * React hook for voice interactions
 * 
 * @returns Object with voice control functions and state
 * 
 * @example
 * ```tsx
 * const { speak, startListening, isListening, isSpeaking } = useVoice()
 * 
 * // Speak text
 * await speak('Hello, how can I help you?')
 * 
 * // Listen for input
 * const userInput = await startListening()
 * console.log('User said:', userInput)
 * 
 * // Ask a question
 * const answer = await ask('What is your name?')
 * console.log('User answered:', answer)
 * ```
 */
export function useVoice(): UseVoiceReturn {
  // State
  const [isSpeaking, setIsSpeaking] = useState(false)
  const [isListening, setIsListening] = useState(false)
  const [isAvailable, setIsAvailable] = useState({ tts: false, stt: false, both: false })

  // Refs
  const voiceManagerRef = useRef<VoiceManager | null>(null)
  const stateCheckIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Initialize VoiceManager
  useEffect(() => {
    // Create VoiceManager instance
    voiceManagerRef.current = new VoiceManager()

    // Check availability after initialization
    const checkAvailability = () => {
      if (voiceManagerRef.current) {
        const availability = voiceManagerRef.current.isAvailable()
        setIsAvailable(availability)
      }
    }

    // Check availability after a short delay to allow initialization
    const initTimeout = setTimeout(checkAvailability, 500)

    // Set up interval to check voice state
    stateCheckIntervalRef.current = setInterval(() => {
      if (voiceManagerRef.current) {
        const state = voiceManagerRef.current.getState()
        setIsSpeaking(state.isSpeaking)
        setIsListening(state.isListening)
      }
    }, 100) // Check every 100ms for responsive UI

    // Cleanup
    return () => {
      clearTimeout(initTimeout)
      
      if (stateCheckIntervalRef.current) {
        clearInterval(stateCheckIntervalRef.current)
      }

      if (voiceManagerRef.current) {
        voiceManagerRef.current.destroy()
        voiceManagerRef.current = null
      }
    }
  }, [])

  /**
   * Speak text using TTS
   */
  const speak = useCallback(async (text: string): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.speak(text)
    } catch (error) {
      console.error('Speak error:', error)
      throw error
    }
  }, [])

  /**
   * Start listening for speech input
   */
  const startListening = useCallback(async (): Promise<string> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      const result = await voiceManagerRef.current.startListening()
      return result
    } catch (error) {
      console.error('Listen error:', error)
      throw error
    }
  }, [])

  /**
   * Stop listening for speech input
   */
  const stopListening = useCallback(async (): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.stopListening()
    } catch (error) {
      console.error('Stop listening error:', error)
      throw error
    }
  }, [])

  /**
   * Ask a question and get spoken response
   */
  const ask = useCallback(async (question: string): Promise<string> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      const result = await voiceManagerRef.current.ask(question)
      return result
    } catch (error) {
      console.error('Ask error:', error)
      throw error
    }
  }, [])

  /**
   * Set TTS language
   */
  const setLanguage = useCallback(async (language: string): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.setLanguage(language)
    } catch (error) {
      console.error('Set language error:', error)
      throw error
    }
  }, [])

  /**
   * Set TTS speech rate
   */
  const setRate = useCallback(async (rate: number): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.setRate(rate)
    } catch (error) {
      console.error('Set rate error:', error)
      throw error
    }
  }, [])

  /**
   * Set TTS pitch
   */
  const setPitch = useCallback(async (pitch: number): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.setPitch(pitch)
    } catch (error) {
      console.error('Set pitch error:', error)
      throw error
    }
  }, [])

  /**
   * Stop all voice operations
   */
  const stopAll = useCallback(async (): Promise<void> => {
    if (!voiceManagerRef.current) {
      throw new Error('VoiceManager not initialized')
    }

    try {
      await voiceManagerRef.current.stopAll()
    } catch (error) {
      console.error('Stop all error:', error)
      throw error
    }
  }, [])

  return {
    speak,
    startListening,
    stopListening,
    ask,
    isSpeaking,
    isListening,
    isAvailable,
    setLanguage,
    setRate,
    setPitch,
    stopAll,
  }
}


package com.que.platform.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Speech Coordinator for agent voice feedback.
 * Provides Text-to-Speech functionality to speak status updates to the user.
 * 
 * Similar to Blurr's SpeechCoordinator.
 */
class SpeechCoordinator private constructor(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val TAG = "SpeechCoordinator"
    
    companion object {
        @Volatile
        private var instance: SpeechCoordinator? = null
        
        fun getInstance(context: Context): SpeechCoordinator {
            return instance ?: synchronized(this) {
                instance ?: SpeechCoordinator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { textToSpeech ->
                    val result = textToSpeech.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Language not supported")
                        isInitialized = false
                    } else {
                        isInitialized = true
                        Log.d(TAG, "TTS initialized successfully")
                    }
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
                isInitialized = false
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Started speaking: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Finished speaking: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Error speaking: $utteranceId")
            }
        })
    }
    
    /**
     * Speak text to the user.
     * 
     * @param text The text to speak
     * @param queueMode Whether to queue or flush previous speech
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak: $text")
            return
        }
        
        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, queueMode, null, "utterance_${System.currentTimeMillis()}")
    }
    
    /**
     * Speak to user (convenience method with default queue mode).
     */
    fun speakToUser(text: String) {
        speak(text, TextToSpeech.QUEUE_ADD)
    }
    
    /**
     * Stop speaking immediately.
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * Check if currently speaking.
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }
    
    /**
     * Shutdown TTS engine.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        Log.d(TAG, "TTS shutdown")
    }
}

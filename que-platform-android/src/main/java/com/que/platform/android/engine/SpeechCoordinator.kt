package com.que.platform.android.engine
import com.que.platform.android.util.STTManager
import com.que.platform.android.util.STTEvent

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect

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
    
    private var pendingVoiceName: String? = null
    private var pendingPitch: Float = 1.0f
    private var pendingRate: Float = 1.0f
    
    companion object {
        @Volatile
        private var instance: SpeechCoordinator? = null
        
        fun getInstance(context: Context): SpeechCoordinator {
            return instance?.apply {
                if (!isInitialized) initializeTTS()
            } ?: synchronized(this) {
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
                        
                        // Apply pending settings
                        pendingVoiceName?.let { setVoice(it) }
                        if (pendingPitch != 1.0f) setPitch(pendingPitch)
                        if (pendingRate != 1.0f) setSpeechRate(pendingRate)
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
            
            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
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

    fun getAvailableVoices(): List<android.speech.tts.Voice> {
        val allVoices = tts?.voices?.toList() ?: emptyList()
        // Filter for local voices that don't need internet AND are English
        return allVoices.filter { 
            !it.isNetworkConnectionRequired && 
            it.locale.language.startsWith("en", ignoreCase = true)
        }
    }

    fun setVoice(voiceName: String) {
        if (!isInitialized) {
            Log.d(TAG, "TTS not ready, queuing voice: $voiceName")
            pendingVoiceName = voiceName
            return
        }
        val voice = tts?.voices?.find { it.name == voiceName }
        if (voice != null) {
            tts?.setLanguage(voice.locale)
            tts?.voice = voice
            pendingVoiceName = null
            Log.d(TAG, "Voice set to: $voiceName (${voice.locale})")
        } else {
            Log.w(TAG, "Voice not found: $voiceName")
        }
    }

    fun setPitch(pitch: Float) {
        if (!isInitialized) {
            pendingPitch = pitch
            return
        }
        tts?.setPitch(pitch)
        pendingPitch = 1.0f
    }

    fun setSpeechRate(rate: Float) {
        if (!isInitialized) {
            pendingRate = rate
            return
        }
        tts?.setSpeechRate(rate)
        pendingRate = 1.0f
    }
    
    private val sttManager: STTManager by lazy { STTManager(context) }

    /**
     * Start listening for user input.
     */
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit
    ) {
        kotlinx.coroutines.MainScope().launch {
            try {
                onListeningStateChange(true)
                sttManager.startListening()
                    .collect { event ->
                        when (event) {
                            is STTEvent.Partial -> onPartialResult(event.text)
                            is STTEvent.Final -> onResult(event.text)
                            is STTEvent.Error -> onError(event.message)
                            else -> {}
                        }
                    }
            } catch (e: Exception) {
                // If STT fails (Timeout, No Match), we come here
                onError(e.message ?: "Unknown STT Error")
            } finally {
                // ALWAYS reset state
                onListeningStateChange(false)
            }
        }
    }

    /**
     * Shutdown TTS engine.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TTS shutdown")
    }
}

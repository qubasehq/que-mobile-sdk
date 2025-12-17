package com.que.platform.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.que.platform.android.voice.PorcupineWakeWordDetector
import kotlinx.coroutines.*
import com.que.platform.android.VisualFeedbackManager

/**
 * Main Service for handling Voice Interaction in Que SDK.
 * Integrates Wake Word, STT/TTS, and Visual Overlays.
 */
class QueConversationalService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    
    // Porcupine Wake Word Detector
    private var wakeWordDetector: PorcupineWakeWordDetector? = null

    companion object {
        private const val TAG = "QueConversational"
        private const val NOTIFICATION_ID = 404
        private const val CHANNEL_ID = "QueVoiceChannel"
        
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QueConversationalService onCreate")
        isRunning = true
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize Wake Word Detector
        setupWakeWord()
    }

    private var apiKey: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        // Retrieve API Key if provided
        // Retrieve API Key if provided in Intent, or fallback to Prefs
        val keyFromIntent = intent?.getStringExtra("EXTRA_API_KEY")
        if (keyFromIntent != null) {
            this.apiKey = keyFromIntent
        } else {
            // Fallback for restart
            val prefs = getSharedPreferences("QuePrefs", 0) // MODE_PRIVATE
            this.apiKey = prefs.getString("API_KEY", null)
            Log.d(TAG, "Restored API Key from prefs: ${this.apiKey != null}")
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission missing.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start listening for wake word if not already interacting
        startWakeWordDetection()
        
        return START_STICKY
    }

    private fun processUserCommand(text: String) {
        serviceScope.launch {
            // 1. Update UI to "Thinking"
            visualFeedbackManager.hideTranscription()
            visualFeedbackManager.showThinkingIndicator()
            
            // 2. Start the Agent Service
            val key = apiKey
            if (key != null) {
                speakResponse("I'm on it.")
                QueAgentService.start(
                    context = this@QueConversationalService,
                    task = text,
                    apiKey = key
                )
                // We don't transition to idle here immediately, 
                // we let the AgentService take over and maybe we listen for its state 
                // or just wait for the user to wake us up again later.
                // For now, let's reset to Wake Word mode so the user can interrupt or give new commands
                // effectively running in parallel? 
                // Actually, if Agent runs, we probably want to stay out of the way or show status.
                // The CosmisOverlayService handles status display.
                // So we just reset ourselves to be ready for next wake word.
            } else {
                speakResponse("I don't have an API key.")
            }
            
            delay(1000) 
            transitionToIdle()
        }
    }

    private fun setupWakeWord() {
        try {
            wakeWordDetector = PorcupineWakeWordDetector(
                context = this,
                onWakeWordDetected = {
                    Log.d(TAG, "Wake Word Detected!")
                    onWakeWordTriggered()
                },
                onApiFailure = { errorMsg ->
                    Log.e(TAG, "Wake Word API Failure: $errorMsg")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this, "Wake Word Error: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Porcupine", e)
            serviceScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(this@QueConversationalService, "Init Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                speakResponse("I couldn't initialize my ears. ${e.message}")
            }
        }
    }

    private fun startWakeWordDetection() {
        Log.d(TAG, "Starting Wake Word Detection")
        if (wakeWordDetector == null) {
            Log.e(TAG, "Wake Word Detector is NULL! Setup must have failed.")
            serviceScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(this@QueConversationalService, "Wake Word Setup Failed!", android.widget.Toast.LENGTH_LONG).show()
                speakResponse("Wake Word Setup Failed.")
            }
            // Try setting up again?
            setupWakeWord()
        }
        wakeWordDetector?.start()
    }

    private fun stopWakeWordDetection() {
        Log.d(TAG, "Stopping Wake Word Detection")
        wakeWordDetector?.stop()
    }

    /**
     * Called when "Hey Que" (or wake word) is detected.
     */
    private fun onWakeWordTriggered() {
        // 1. Stop Wake Word to free mic
        stopWakeWordDetection()
        
        // 2. Show Visual Feedback
        serviceScope.launch(Dispatchers.Main) {
            visualFeedbackManager.showSpeakingOverlay()
            visualFeedbackManager.showTtsWave()
            visualFeedbackManager.showTranscription("Listening...")
            visualFeedbackManager.setWaveTargetAmplitude(1.0f) // Visually indicate listening
        }

        // 3. Start Listening for Command
        startListeningForCommand()
    }

    private fun startListeningForCommand() {
        Log.d(TAG, "Starting STT for user command")
        
        speechCoordinator.startListening(
            onResult = { text ->
                Log.d(TAG, "User said: $text")
                visualFeedbackManager.updateTranscription(text)
                
                // Process the command
                processUserCommand(text)
            },
            onError = { error ->
                Log.e(TAG, "STT Error: $error")
                visualFeedbackManager.updateTranscription("Error: $error")
                transitionToIdle()
            },
            onPartialResult = { partial: String ->
                visualFeedbackManager.updateTranscription(partial)
            },
            onListeningStateChange = { isListening: Boolean ->
                 // Update visual wave based on listening state if needed
                 if (!isListening) {
                     visualFeedbackManager.setWaveTargetAmplitude(0.0f)
                 }
            }
        )
    }



    private fun speakResponse(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            // Visuals for speaking
             visualFeedbackManager.setWaveTargetAmplitude(0.8f) // Active wave
        }
        
        speechCoordinator.speakToUser(text) // Using the simpler speakToUser for now
        
        // Wait for speech to finish (approximate or use callback if available)
        serviceScope.launch {
            delay(2000) 
            transitionToIdle()
        }
    }

    private fun transitionToIdle() {
        serviceScope.launch(Dispatchers.Main) {
            // Hide overlays
            visualFeedbackManager.cleanup()
            
            // Resume Wake Word
            startWakeWordDetection()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Que Voice Agent")
            .setContentText("Listening for 'Hey Que'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopWakeWordDetection()
        visualFeedbackManager.cleanup()
        serviceScope.cancel()
        Log.d(TAG, "Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

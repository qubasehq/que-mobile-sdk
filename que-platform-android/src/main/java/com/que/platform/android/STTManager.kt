package com.que.platform.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Manages Speech-to-Text using Android's SpeechRecognizer.
 */
class STTManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(): Flow<String> = callbackFlow {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // close(RuntimeException("Speech recognition error: $error"))
                // Don't close flow on error, just maybe log or ignore for continuous listening?
                // For now, let's close to signal stop.
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(matches[0])
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Optional: emit partials
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        with(kotlinx.coroutines.Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(listener)
                speechRecognizer?.startListening(intent)
            } else {
                close(RuntimeException("Speech recognition not available"))
            }
        }

        awaitClose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}

package com.que.platform.android.util

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
 * Events emitted by STTManager
 */
sealed class STTEvent {
    data class Partial(val text: String) : STTEvent()
    data class Final(val text: String) : STTEvent()
    data class Volume(val rmsdB: Float) : STTEvent()
    data class Error(val message: String) : STTEvent()
    object EndOfSpeech : STTEvent()
}

/**
 * Manages Speech-to-Text using Android's SpeechRecognizer.
 */
class STTManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(): Flow<STTEvent> = callbackFlow {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Reduce silence timeouts so onEndOfSpeech fires quickly after user stops talking
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                trySend(STTEvent.Volume(rmsdB))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                trySend(STTEvent.EndOfSpeech)
            }
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                    else -> "Error $error"
                }
                trySend(STTEvent.Error(errorMessage))
                close() 
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(STTEvent.Final(matches[0]))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(STTEvent.Partial(matches[0]))
                }
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

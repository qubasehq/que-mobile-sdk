package com.que.platform.android.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Wake Word Detector using Porcupine.
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onApiFailure: (String) -> Unit
) {

    private var porcupineManager: PorcupineManager? = null
    
    companion object {
        private const val TAG = "PorcupineDetector"
        private const val KEYWORD_ASSET_NAME = "Hey-Que_en_android_v4_0_0.ppn"
        // TODO: Replace with secure key management
        private const val ACCESS_KEY = "mdXqwAgZE8FrAdJESmuVlTX/5lDGc0T1mDKH25sKC46//P0HR7fTkg=="
    }

    fun start() {
        try {
            if (porcupineManager == null) {
                // Ensure keyword file is available
                val keywordFile = File(context.filesDir, KEYWORD_ASSET_NAME)
                if (!keywordFile.exists()) {
                    copyContextFileToStorage(context, KEYWORD_ASSET_NAME, keywordFile)
                }

                if (!keywordFile.exists()) {
                    Log.e(TAG, "Failed to copy keyword file")
                    onApiFailure("Could not load wake word model")
                    return
                }

                // Initialize Porcupine
                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(ACCESS_KEY)
                    .setKeywordPath(keywordFile.absolutePath)
                    .build(context) { onWakeWordDetected() }
                 
                 Log.d(TAG, "PorcupineManager initialized successfully")
            }
            porcupineManager?.start()
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine error: ${e.message}", e)
            onApiFailure(e.message ?: "Unknown Porcupine error")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing wake word", e)
            onApiFailure(e.localizedMessage ?: "Unexpected error")
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Porcupine", e)
        }
    }
    
    fun cleanup() {
        try {
            porcupineManager?.delete()
            porcupineManager = null
        } catch (e: Exception) {
             Log.e(TAG, "Error cleaning up Porcupine", e)
        }
    }

    private fun copyContextFileToStorage(context: Context, filename: String, outputFile: File) {
        try {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset file: $filename", e)
        }
    }
}

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
    }

    fun start(accessKey: String) {
        Log.d(TAG, ">>> Attempting to start Porcupine...")
        if (accessKey.isBlank()) {
            Log.e(TAG, ">>> Porcupine accessKey is BLANK. Cannot start.")
            onApiFailure("Wake word detection disabled: Missing Access Key")
            return
        }
        
        Log.d(TAG, ">>> Using accessKey (masked): ${accessKey.take(4)}...${accessKey.takeLast(4)} (length=${accessKey.length})")
        
        // Always cleanup previous instance to allow fresh init
        if (porcupineManager != null) {
            Log.d(TAG, ">>> Cleaning up existing PorcupineManager before re-init")
            try { porcupineManager?.stop() } catch (_: Exception) {}
            try { porcupineManager?.delete() } catch (_: Exception) {}
            porcupineManager = null
        }
        
        try {
            // ALWAYS re-copy keyword file from assets to ensure we have the latest version
            val keywordFile = File(context.filesDir, KEYWORD_ASSET_NAME)
            Log.d(TAG, ">>> Keyword file path: ${keywordFile.absolutePath}")
            
            if (keywordFile.exists()) {
                Log.d(TAG, ">>> Deleting old cached keyword file (size=${keywordFile.length()})")
                keywordFile.delete()
            }
            
            Log.d(TAG, ">>> Copying fresh keyword file from assets...")
            copyContextFileToStorage(context, KEYWORD_ASSET_NAME, keywordFile)

            if (!keywordFile.exists() || keywordFile.length() == 0L) {
                Log.e(TAG, ">>> FAILED to copy keyword file from assets!")
                onApiFailure("Could not load wake word model file")
                return
            }
            Log.d(TAG, ">>> Keyword file ready: size=${keywordFile.length()} bytes")

            // Try custom keyword first
            Log.d(TAG, ">>> Attempting PorcupineManager.Builder with CUSTOM keyword...")
            porcupineManager = try {
                PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordFile.absolutePath)
                    .setSensitivity(0.7f)
                    .build(context) { 
                        Log.d(TAG, ">>> WAKE WORD 'Hey Que' DETECTED!")
                        onWakeWordDetected() 
                    }
            } catch (e: Exception) {
                Log.w(TAG, ">>> Custom model FAILED: ${e.message}")
                Log.d(TAG, ">>> Trying built-in keyword 'PORCUPINE' as fallback...")
                try {
                    PorcupineManager.Builder()
                        .setAccessKey(accessKey)
                        .setKeywords(arrayOf(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.PORCUPINE))
                        .setSensitivity(0.7f)
                        .build(context) {
                            Log.d(TAG, ">>> BUILT-IN WAKE WORD 'Porcupine' DETECTED!")
                            onWakeWordDetected()
                        }
                } catch (e2: Exception) {
                    Log.e(TAG, ">>> Built-in keyword ALSO FAILED: ${e2.message}")
                    Log.e(TAG, ">>> ACCESS KEY IS LIKELY INVALID OR EXPIRED")
                    onApiFailure("Porcupine Error: Both custom and built-in keywords failed. Key may be invalid.")
                    return
                }
            }
              
            Log.d(TAG, ">>> PorcupineManager created successfully! Calling start()...")
            porcupineManager?.start()
            Log.i(TAG, ">>> 🚀 Porcupine Wake Word detection STARTED successfully!")
        } catch (e: PorcupineException) {
            Log.e(TAG, ">>> Porcupine native error: ${e.message}", e)
            onApiFailure("Porcupine Error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, ">>> Unexpected error: ${e.message}", e)
            onApiFailure("Internal Error: ${e.localizedMessage}")
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

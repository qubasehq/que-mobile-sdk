package com.que.platform.android.llm

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads GGUF model files from HuggingFace with progress tracking.
 * Uses OkHttp for HTTP downloads, stores models in context.filesDir/models/.
 */
class ModelDownloadManager(private val context: Context) {

    private val TAG = "ModelDownloadManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var currentJob: Job? = null

    /**
     * Check if there is enough storage space for the model.
     */
    fun hasEnoughSpace(model: LocalModelInfo): Boolean {
        val modelsDir = LocalModelRegistry.getModelsDir(context)
        val stat = StatFs(modelsDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        // Require at least 500MB extra headroom
        return availableBytes > model.sizeBytes + 500_000_000L
    }

    /**
     * Start downloading a model. Emits progress via downloadState flow.
     */
    suspend fun downloadModel(model: LocalModelInfo) = coroutineScope {
        if (_downloadState.value is DownloadState.Downloading) {
            Log.w(TAG, "Download already in progress")
            return@coroutineScope
        }

        if (!hasEnoughSpace(model)) {
            _downloadState.value = DownloadState.Error(model.id, "Not enough storage space")
            return@coroutineScope
        }

        if (LocalModelRegistry.isModelDownloaded(context, model)) {
            Log.i(TAG, "Model already downloaded: $" + "{model.name}")
            _downloadState.value = DownloadState.Completed(model.id)
            return@coroutineScope
        }

        currentJob = launch(Dispatchers.IO) {
            try {
                val destFile = File(LocalModelRegistry.getModelsDir(context), model.filename)
                val tempFile = File(destFile.absolutePath + ".tmp")

                Log.i(TAG, "Starting download: $" + "{model.name} -> $" + "{destFile.absolutePath}")
                _downloadState.value = DownloadState.Downloading(model.id, 0, model.sizeBytes)

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Error(model.id, "HTTP $" + "{response.code}")
                    return@launch
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState.Error(model.id, "Empty response body")
                    return@launch
                }

                val totalBytes = body.contentLength().let { if (it > 0) it else model.sizeBytes }
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(8192)
                var bytesDownloaded = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var lastBytes = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Update progress every 500ms
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 500) {
                                val timeDelta = (now - lastUpdateTime) / 1000.0
                                val bytesDelta = bytesDownloaded - lastBytes
                                val speed = if (timeDelta > 0) (bytesDelta / timeDelta).toLong() else 0L
                                val remaining = totalBytes - bytesDownloaded
                                val eta = if (speed > 0) remaining / speed else 0L

                                _downloadState.value = DownloadState.Downloading(
                                    modelId = model.id,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta
                                )
                                lastUpdateTime = now
                                lastBytes = bytesDownloaded
                            }
                        }
                    }
                }

                if (!isActive) {
                    // Download was cancelled
                    tempFile.delete()
                    _downloadState.value = DownloadState.Idle
                    return@launch
                }

                // Rename temp file to final
                tempFile.renameTo(destFile)
                Log.i(TAG, "Download complete: $" + "{model.name}")
                _downloadState.value = DownloadState.Completed(model.id)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: $" + "{e.message}", e)
                _downloadState.value = DownloadState.Error(model.id, e.message ?: "Unknown error")
            }
        }
        currentJob?.join()
    }

    /**
     * Cancel the current download.
     */
    fun cancelDownload() {
        currentJob?.cancel()
        currentJob = null
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Delete a downloaded model file.
     */
    fun deleteModel(model: LocalModelInfo): Boolean {
        val file = File(LocalModelRegistry.getModelsDir(context), model.filename)
        val tempFile = File(file.absolutePath + ".tmp")
        tempFile.delete()
        return if (file.exists()) file.delete() else true
    }
}

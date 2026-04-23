package com.example.lazycal

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class ModelManager(private val context: Context) {
    private val modelFileName = "gemma-4-E2B-it.litertlm"
    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    
    val modelFile: File by lazy {
        // DownloadManager cannot write directly to the app's internal filesDir (/data/data/...)
        // because it is a system service running in a different process.
        // We use getExternalFilesDir(null) which is app-specific but accessible to the DownloadManager.
        File(context.getExternalFilesDir(null), modelFileName)
    }

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    fun isModelDownloaded(): Boolean = modelFile.exists()

    fun downloadModel() {
        if (isModelDownloaded()) return

        try {
            // Ensure the destination directory exists
            modelFile.parentFile?.mkdirs()

            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("Downloading Gemma Model")
                .setDescription("Downloading LiteRT-LM model for local chat")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Use setDestinationInExternalFilesDir instead of setDestinationUri(filesDir)
                // This allows the system's DownloadManager process to write the file.
                .setDestinationInExternalFilesDir(context, null, modelFileName)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            _downloadProgress.value = 0f
        } catch (e: Exception) {
            e.printStackTrace()
            // In a real app, you'd want to propagate this error to the UI
        }
    }
}

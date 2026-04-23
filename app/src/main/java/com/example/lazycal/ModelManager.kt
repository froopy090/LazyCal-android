package com.example.lazycal

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class ModelManager(private val context: Context) {
    private val modelFileName = "gemma-4-E2B-it.litertlm"
    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    
    val modelFile: File by lazy {
        File(context.filesDir, modelFileName)
    }

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    fun isModelDownloaded(): Boolean = modelFile.exists()

    fun downloadModel() {
        if (isModelDownloaded()) return

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Gemma Model")
            .setDescription("Downloading LiteRT-LM model for local chat")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(modelFile))

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        _downloadProgress.value = 0f

        // Simple progress tracking (in a real app, you'd use a more robust way to poll DownloadManager)
        // For brevity, we'll just set it to indeterminate/starting state
    }
    
    // In a real implementation, you'd register a receiver to update progress and completion
}

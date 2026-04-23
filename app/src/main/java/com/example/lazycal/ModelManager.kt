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
        File(context.getExternalFilesDir(null), modelFileName)
    }

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    fun isModelDownloaded(): Boolean = modelFile.exists()

    fun downloadModel() {
        if (isModelDownloaded()) return

        try {
            modelFile.parentFile?.mkdirs()

            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("Downloading Gemma Model")
                .setDescription("Downloading LiteRT-LM model for local chat")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, modelFileName)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            _downloadProgress.value = 0f
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteModel(): Boolean {
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            false
        }
    }
}

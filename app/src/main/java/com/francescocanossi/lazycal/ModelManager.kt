package com.francescocanossi.lazycal

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File

sealed class DownloadStatus {
    object None : DownloadStatus()
    data class Running(val bytes: Long, val total: Long) : DownloadStatus()
    data class Paused(val bytes: Long, val total: Long, val reason: Int) : DownloadStatus()
    object Succeeded : DownloadStatus()
    data class Failed(val reason: Int) : DownloadStatus()
}

sealed class StartResult {
    data class Started(val id: Long) : StartResult()
    object AlreadyDownloaded : StartResult()
    data class NotEnoughSpace(val requiredBytes: Long, val availableBytes: Long) : StartResult()
    data class Failed(val message: String) : StartResult()
}

class ModelManager(private val context: Context) {
    private val modelFileName = "gemma-4-E2B-it.litertlm"
    private val partFileName = "$modelFileName.part"

    // Pinned to a specific commit so the expected size below can never drift
    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$MODEL_REVISION/$modelFileName"

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val modelFile: File by lazy {
        File(context.getExternalFilesDir(null), modelFileName)
    }

    private val partFile: File by lazy {
        File(context.getExternalFilesDir(null), partFileName)
    }

    val activeDownloadId: Long
        get() = prefs.getLong(KEY_DOWNLOAD_ID, -1L)

    fun isModelDownloaded(): Boolean {
        // Exact size check: a partial or truncated file must never be handed to the native engine
        return modelFile.exists() && modelFile.length() == EXPECTED_SIZE_BYTES
    }

    fun startDownload(): StartResult {
        if (isModelDownloaded()) return StartResult.AlreadyDownloaded

        // Reuse a download that is already queued or running instead of enqueuing a duplicate
        when (queryStatus()) {
            is DownloadStatus.Running, is DownloadStatus.Paused -> return StartResult.Started(activeDownloadId)
            else -> clearActiveDownload(removeFromSystem = true)
        }

        return try {
            val dir = context.getExternalFilesDir(null)
                ?: return StartResult.Failed("Storage is not available.")
            dir.mkdirs()
            // Remove stale or truncated files left by older versions or failed downloads
            if (modelFile.exists()) modelFile.delete()
            if (partFile.exists()) partFile.delete()

            val required = EXPECTED_SIZE_BYTES + SPACE_MARGIN_BYTES
            val available = availableBytes(dir)
            if (available < required) {
                return StartResult.NotEnoughSpace(required, available)
            }

            val request = DownloadManager.Request(modelUrl.toUri())
                .setTitle("LazyCal AI model")
                .setDescription("Downloading the on-device AI model (2.58 GB)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(context, null, partFileName)

            val id = downloadManager.enqueue(request)
            prefs.edit { putLong(KEY_DOWNLOAD_ID, id) }
            StartResult.Started(id)
        } catch (e: Exception) {
            StartResult.Failed(e.message ?: "Failed to start download")
        }
    }

    fun queryStatus(): DownloadStatus {
        val id = activeDownloadId
        if (id == -1L) return DownloadStatus.None

        return try {
            downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return DownloadStatus.None
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val reportedTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val total = if (reportedTotal > 0) reportedTotal else EXPECTED_SIZE_BYTES
                when (status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> DownloadStatus.Running(bytes, total)
                    DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused(bytes, total, reason)
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Succeeded
                    DownloadManager.STATUS_FAILED -> DownloadStatus.Failed(reason)
                    else -> DownloadStatus.None
                }
            } ?: DownloadStatus.None
        } catch (_: Exception) {
            DownloadStatus.None
        }
    }

    /**
     * Moves the completed download into place. Returns true only if the final model file is complete.
     */
    fun finalizeDownload(): Boolean {
        if (isModelDownloaded()) {
            clearActiveDownload(removeFromSystem = false)
            return true
        }
        if (!partFile.exists() || partFile.length() != EXPECTED_SIZE_BYTES) {
            return false
        }
        if (modelFile.exists()) modelFile.delete()
        val renamed = partFile.renameTo(modelFile)
        if (renamed) clearActiveDownload(removeFromSystem = false)
        return renamed && isModelDownloaded()
    }

    fun cancelDownload() {
        clearActiveDownload(removeFromSystem = true)
        if (partFile.exists()) partFile.delete()
    }

    fun deleteModel(): Boolean {
        cancelDownload()
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            false
        }
    }

    private fun availableBytes(dir: File): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Includes cached data the system can clear to make room
                val storageManager = context.getSystemService(StorageManager::class.java)
                return storageManager.getAllocatableBytes(storageManager.getUuidForPath(dir))
            } catch (_: Exception) {
                // Fall back to usableSpace
            }
        }
        return dir.usableSpace
    }

    private fun clearActiveDownload(removeFromSystem: Boolean) {
        val id = activeDownloadId
        if (removeFromSystem && id != -1L) {
            try {
                // Also deletes the partially downloaded file
                downloadManager.remove(id)
            } catch (_: Exception) {
                // Download may already be gone
            }
        }
        prefs.edit { remove(KEY_DOWNLOAD_ID) }
    }

    companion object {
        const val MODEL_REVISION = "c658da437244f072bf2f1cabc916b60d66d97663"
        const val EXPECTED_SIZE_BYTES = 2_588_147_712L
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
        private const val PREFS_NAME = "model_download"
        private const val KEY_DOWNLOAD_ID = "download_id"

        fun pausedReasonText(reason: Int): String = when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network…"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi…"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection interrupted, retrying…"
            else -> "Paused…"
        }

        fun failedReasonText(reason: Int): String = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space to download the AI model."
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is not available."
            DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_TOO_MANY_REDIRECTS,
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The download server had a problem. Please try again."
            DownloadManager.ERROR_CANNOT_RESUME -> "The download could not be resumed. Please try again."
            else -> "The AI model download failed. Please try again."
        }
    }
}

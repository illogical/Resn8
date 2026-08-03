package com.app.resn8.storage.indexer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.resn8.Resn8Application
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class IndexingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        var phase = "PREPARE"
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return@coroutineScope Result.failure(
            workDataOf(KEY_ERROR_PHASE to phase, KEY_ERROR_CATEGORY to "MissingInput")
        )
        val treeUri = inputData.getString(KEY_TREE_URI)?.let(Uri::parse) ?: return@coroutineScope Result.failure(
            workDataOf(KEY_ERROR_PHASE to phase, KEY_ERROR_CATEGORY to "MissingInput")
        )
        setForeground(createForegroundInfo("Preparing music index"))

        val container = (applicationContext as Resn8Application).container
        val orchestrator = ScanOrchestrator(
            context = applicationContext,
            mediaRepository = container.mediaRepository,
            collectionRepository = container.collectionRepository
        )
        phase = "PROGRESS_OBSERVE"
        val progressJob = launch {
            orchestrator.scanProgress.filterNotNull().collect { progress ->
                setProgress(
                    workDataOf(
                        KEY_STARTED_AT to progress.startedAt,
                        KEY_PHASE to progress.phase,
                        KEY_FOLDERS to progress.scannedFolders,
                        KEY_DOCUMENTS to progress.inspectedDocuments,
                        KEY_AUDIO to progress.admittedAudio,
                        KEY_UNSUPPORTED to progress.unsupportedCount,
                        KEY_UNREADABLE to progress.unreadableCount,
                        KEY_METADATA_FAILURES to progress.metadataFailureCount,
                        KEY_ARTWORK to progress.artworkCandidateCount
                    )
                )
                setForeground(
                    createForegroundInfo(
                        "Indexed ${progress.admittedAudio} tracks • ${progress.inspectedDocuments} files checked"
                    )
                )
            }
        }
        try {
            phase = "SCAN_EXECUTE"
            val result = orchestrator.executeScan(sourceId, treeUri)
            Result.success(
                workDataOf(
                    KEY_AUDIO to result.scannedCount,
                    KEY_DURATION_MS to result.durationMs
                )
            )
        } catch (e: Exception) {
            val category = if (isStopped) "CANCELLED" else (e::class.simpleName ?: "IndexingFailure")
            Log.e(LOG_TAG, "indexing_failed workId=$id phase=$phase category=$category")
            Result.failure(workDataOf(KEY_ERROR_PHASE to phase, KEY_ERROR_CATEGORY to category))
        } finally {
            progressJob.cancel()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Preparing music index")

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Music indexing", NotificationManager.IMPORTANCE_LOW)
        )
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Resn8 is indexing Music")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_PHASE = "phase"
        const val KEY_FOLDERS = "folders"
        const val KEY_DOCUMENTS = "documents"
        const val KEY_AUDIO = "audio"
        const val KEY_UNSUPPORTED = "unsupported"
        const val KEY_UNREADABLE = "unreadable"
        const val KEY_METADATA_FAILURES = "metadata_failures"
        const val KEY_ARTWORK = "artwork"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_ERROR_CATEGORY = "error_category"
        const val KEY_ERROR_PHASE = "error_phase"
        private const val CHANNEL_ID = "resn8_indexing"
        private const val NOTIFICATION_ID = 17107
        private const val LOG_TAG = "Resn8IndexingWorker"

        fun uniqueWorkName(sourceId: String) = "index-source-$sourceId"

        fun enqueue(context: Context, sourceId: String, treeUri: String) {
            val request = OneTimeWorkRequestBuilder<IndexingWorker>()
                .setInputData(workDataOf(KEY_SOURCE_ID to sourceId, KEY_TREE_URI to treeUri))
                .addTag(uniqueWorkName(sourceId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(sourceId),
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

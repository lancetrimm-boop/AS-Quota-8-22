package com.example.data

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.example.data.db.ConversionJobDao
import com.example.data.db.ConversionJobEntity
import kotlinx.coroutines.flow.Flow
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository for managing the persistent conversion queue.
 */
class ConversionQueueRepository(
    private val dao: com.example.data.db.ConversionJobDao,
    private val prefDao: com.example.data.db.UserPreferenceDao,
    private val workManager: WorkManager
) {

    fun observeAllJobs(): Flow<List<ConversionJobEntity>> = dao.observeAllJobs()

    fun observeActiveJobs(): Flow<List<ConversionJobEntity>> = dao.observeActiveJobs()

    suspend fun isAutoCleanupEnabled(): Boolean {
        val pref = prefDao.getPreference(ConversionConstants.PREF_AUTO_CLEANUP_ENABLED)
        return pref?.value != "false"
    }

    suspend fun setAutoCleanupEnabled(enabled: Boolean) {
        prefDao.insertPreference(com.example.data.db.UserPreferenceEntity(
            ConversionConstants.PREF_AUTO_CLEANUP_ENABLED, enabled.toString()
        ))
    }

    suspend fun enqueueConversions(candidates: List<ConversionCandidate>) {
        val jobs = candidates.map { candidate ->
            ConversionJobEntity(
                mediaId = candidate.mediaId,
                sourceUri = candidate.sourceUri.toString(),
                fileName = candidate.fileName,
                mediaTitle = candidate.mediaTitle,
                sourceVideoCodec = candidate.recommendation.sourceVideoCodec,
                sourceAudioCodec = candidate.recommendation.sourceAudioCodec,
                targetContainer = candidate.recommendation.targetContainer,
                targetVideoCodec = candidate.recommendation.targetVideoCodec,
                targetAudioCodec = candidate.recommendation.targetAudioCodec,
                status = ConversionJobStatus.QUEUED.name
            )
        }
        
        // In a real production app, we'd use a transaction here.
        // For this phase, we insert them and then schedule the work.
        jobs.forEach { job ->
            val id = dao.insert(job)
            scheduleWork(id)
        }
    }

    private fun scheduleWork(jobId: Long) {
        val data = workDataOf("jobId" to jobId)
        
        // Sequential processing via unique work with APPEND
        val request = OneTimeWorkRequestBuilder<com.example.util.MediaConversionWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build())
            .build()

        workManager.enqueueUniqueWork(
            "conversion_queue",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        
        scheduleCleanupWorker()
    }

    private fun scheduleCleanupWorker() {
        val cleanupRequest = PeriodicWorkRequestBuilder<com.example.util.OriginalMediaCleanupWorker>(
            1, TimeUnit.DAYS
        ).setConstraints(Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            "original_media_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    suspend fun cancelJob(jobId: Long) {
        val job = dao.getJobById(jobId)
        if (job != null) {
            dao.updateStatus(jobId, ConversionJobStatus.CANCELLED.name)
            // WorkManager unique work with APPEND makes individual cancellation tricky if it's the active one.
            // For now, we mark as cancelled in DB, and the worker will check status.
        }
    }

    suspend fun retryJob(jobId: Long) {
        val job = dao.getJobById(jobId)
        if (job != null) {
            dao.update(job.copy(
                status = ConversionJobStatus.QUEUED.name,
                attemptCount = job.attemptCount + 1,
                progress = 0,
                errorMessage = null,
                failureStage = null
            ))
            scheduleWork(jobId)
        }
    }

    suspend fun clearCompleted() {
        dao.clearCompleted()
    }

    suspend fun cleanupOriginalNow(jobId: Long) {
        val data = workDataOf("jobId" to jobId)
        val request = OneTimeWorkRequestBuilder<com.example.util.OriginalMediaCleanupWorker>()
            .setInputData(data)
            .build()
        
        workManager.enqueueUniqueWork(
            "manual_cleanup_$jobId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Attempts a direct physical deletion. This is intended to be called from the UI 
     * layer to handle any required user authorization (SecurityException).
     */
    suspend fun performDirectCleanup(context: android.content.Context, jobId: Long): Result<Unit> {
        val job = dao.getJobById(jobId) ?: return Result.failure(Exception("Job not found"))
        val sourceUri = Uri.parse(job.sourceUri)
        
        return try {
            val deleted = context.contentResolver.delete(sourceUri, null, null) > 0
            if (deleted || !uriExists(context, sourceUri)) {
                dao.update(job.copy(
                    cleanupStatus = OriginalCleanupStatus.CLEANUP_COMPLETED.name,
                    cleanupCompletedTimestamp = System.currentTimeMillis(),
                    status = ConversionJobStatus.CLEANUP_COMPLETED.name,
                    updatedTimestamp = System.currentTimeMillis()
                ))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Deletion returned 0 rows"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uriExists(context: android.content.Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun replaceOriginal(context: android.content.Context, jobId: Long): ReplacementResult {
        val job = dao.getJobById(jobId) ?: return ReplacementResult(ReplacementStage.FAILED, errorMessage = "Job not found")
        
        if (job.status != ConversionJobStatus.COMPLETED.name) {
            return ReplacementResult(ReplacementStage.FAILED, errorMessage = "Conversion not completed or validated")
        }

        dao.updateStatus(jobId, ConversionJobStatus.REPLACING.name)
        
        val result = AuraMediaReplacementManager.replaceOriginalWithConverted(context, job) { stage, finalUri ->
            dao.update(job.copy(
                replacementStage = stage.name,
                finalMediaUri = finalUri ?: job.finalMediaUri,
                updatedTimestamp = System.currentTimeMillis()
            ))
        }

        if (result.stage == ReplacementStage.READY_FOR_ORIGINAL_CLEANUP) {
            dao.update(job.copy(
                status = ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP.name,
                replacementStage = result.stage.name,
                finalMediaUri = result.finalMediaUri,
                finalMediaId = result.finalMediaId,
                cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name,
                cleanupEligibilityTimestamp = result.cleanupEligibilityTimestamp,
                updatedTimestamp = System.currentTimeMillis()
            ))
        } else {
            dao.update(job.copy(
                status = ConversionJobStatus.FAILED.name,
                replacementStage = result.stage.name,
                errorMessage = result.errorMessage,
                updatedTimestamp = System.currentTimeMillis()
            ))
        }

        return result
    }
}

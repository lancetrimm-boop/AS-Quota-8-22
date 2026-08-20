package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.compatibility.AuraMediaTranscoder
import com.example.data.*
import com.example.data.db.AuraDatabase
import com.example.data.db.ConversionJobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager worker for performing local media conversion in the background.
 */
class MediaConversionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AuraDatabase.getInstance(context).conversionJobDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getLong("jobId", -1L)
        if (jobId == -1L) return@withContext Result.failure()

        val job = dao.getJobById(jobId) ?: return@withContext Result.failure()

        // 1. Check if already cancelled
        if (job.status == ConversionJobStatus.CANCELLED.name) {
            return@withContext Result.success()
        }

        try {
            updateJob(job.copy(
                status = ConversionJobStatus.PREPARING.name,
                startedTimestamp = System.currentTimeMillis(),
                updatedTimestamp = System.currentTimeMillis()
            ))

            val sourceUri = Uri.parse(job.sourceUri)

            // 2. Perform Transcoding & Validation via AuraMediaTranscoder
            val result = AuraMediaTranscoder.transcodeAndValidate(applicationContext, sourceUri) { stage, progress ->
                // Update persistent progress
                // Since this callback is inside withContext(Dispatchers.IO), we can just run a blocking update or launch a sibling.
                // However, Room shouldn't be blocked too long.
                runBlocking {
                    dao.updateProgress(jobId, progress)
                    dao.updateStatus(jobId, mapStageToStatus(stage).name)
                }
            }

            // 3. Persist Transcoding Result
            val transcodeSuccess = result.status == ConversionStatus.CONVERTED
            val intermediateStatus = if (transcodeSuccess) {
                ConversionJobStatus.COMPLETED
            } else {
                ConversionJobStatus.FAILED
            }

            dao.update(job.copy(
                status = intermediateStatus.name,
                progress = if (transcodeSuccess) 100 else job.progress,
                completedTimestamp = if (transcodeSuccess) System.currentTimeMillis() else null,
                updatedTimestamp = System.currentTimeMillis(),
                outputPath = result.outputPath,
                failureStage = result.failureStage.name,
                errorMessage = result.errorMessage,
                realTimeFactor = result.realTimeFactor,
                compressionRatio = result.compressionRatio
            ))

            if (!transcodeSuccess) return@withContext Result.failure()

            // 4. AUTOMATIC REPLACEMENT (Phase 5)
            Log.i("MediaConversionWorker", "Transcoding successful for job $jobId. Triggering automatic replacement.")
            
            // Reload job to get updated outputPath and status
            val readyJob = dao.getJobById(jobId) ?: return@withContext Result.failure()
            
            val replacementResult = AuraMediaReplacementManager.replaceOriginalWithConverted(
                applicationContext,
                readyJob
            ) { stage, finalUri ->
                // Reload job to ensure we don't overwrite other field updates from worker
                val currentJob = dao.getJobById(jobId) ?: readyJob
                dao.update(currentJob.copy(
                    status = ConversionJobStatus.REPLACING.name,
                    replacementStage = stage.name,
                    finalMediaUri = finalUri ?: currentJob.finalMediaUri,
                    updatedTimestamp = System.currentTimeMillis()
                ))
            }

            if (replacementResult.stage == ReplacementStage.READY_FOR_ORIGINAL_CLEANUP) {
                dao.update(readyJob.copy(
                    status = ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP.name,
                    replacementStage = replacementResult.stage.name,
                    finalMediaUri = replacementResult.finalMediaUri,
                    finalMediaId = replacementResult.finalMediaId,
                    cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name,
                    cleanupEligibilityTimestamp = replacementResult.cleanupEligibilityTimestamp,
                    updatedTimestamp = System.currentTimeMillis()
                ))
                return@withContext Result.success()
            } else {
                dao.update(readyJob.copy(
                    status = ConversionJobStatus.FAILED.name,
                    replacementStage = replacementResult.stage.name,
                    errorMessage = replacementResult.errorMessage,
                    updatedTimestamp = System.currentTimeMillis()
                ))
                return@withContext Result.failure()
            }

        } catch (e: Exception) {
            Log.e("MediaConversionWorker", "Worker failed for job $jobId", e)
            dao.update(job.copy(
                status = ConversionJobStatus.FAILED.name,
                errorMessage = e.message ?: "Unknown worker exception",
                updatedTimestamp = System.currentTimeMillis()
            ))
            return@withContext Result.failure()
        }
    }

    private suspend fun updateJob(job: ConversionJobEntity) {
        dao.update(job)
    }

    private fun mapStageToStatus(stage: ConversionStage): ConversionJobStatus {
        return when (stage) {
            ConversionStage.IDLE -> ConversionJobStatus.QUEUED
            ConversionStage.PREPARING -> ConversionJobStatus.PREPARING
            ConversionStage.CONVERTING -> ConversionJobStatus.CONVERTING
            ConversionStage.VALIDATING -> ConversionJobStatus.VALIDATING
            ConversionStage.TESTING_PLAYBACK -> ConversionJobStatus.TESTING_PLAYBACK
            ConversionStage.COMPLETE -> ConversionJobStatus.COMPLETED
            ConversionStage.FAILED -> ConversionJobStatus.FAILED
        }
    }
}

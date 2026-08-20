package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.compatibility.AuraPlaybackValidator
import com.example.data.*
import com.example.data.db.AuraDatabase
import com.example.data.db.ConversionJobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Worker responsible for the safe physical deletion of original media files
 * after a successful and stable replacement.
 */
class OriginalMediaCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AuraDatabase.getInstance(context)
    private val dao = db.conversionJobDao()
    private val prefDao = db.userPreferenceDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val specificJobId = inputData.getLong("jobId", -1L)
        
        if (specificJobId != -1L) {
            Log.i("AuraCleanupWorker", "[$specificJobId] Manual cleanup request initiated.")
            val job = dao.getJobById(specificJobId) ?: return@withContext Result.failure()
            return@withContext if (processCleanup(job, isManual = true)) Result.success() else Result.failure()
        }

        Log.d("AuraCleanupWorker", "Starting scheduled background cleanup check.")
        
        // Check if automatic cleanup is globally disabled
        val autoCleanupPref = prefDao.getPreference(ConversionConstants.PREF_AUTO_CLEANUP_ENABLED)
        if (autoCleanupPref?.value == "false") {
            Log.i("AuraCleanupWorker", "Automatic cleanup is disabled by user preference.")
            return@withContext Result.success()
        }

        val pendingJobs = dao.getCleanupPendingJobs()
        if (pendingJobs.isEmpty()) {
            Log.d("AuraCleanupWorker", "No pending cleanup jobs found.")
            return@withContext Result.success()
        }

        var allSuccess = true
        pendingJobs.forEach { job ->
            try {
                val result = processCleanup(job, isManual = false)
                if (!result) {
                    allSuccess = false
                }
            } catch (e: Exception) {
                Log.e("AuraCleanupWorker", "Fatal error during cleanup process: ${e.message}", e)
                allSuccess = false
            }
        }

        return@withContext if (allSuccess) Result.success() else Result.retry()
    }

    private suspend fun processCleanup(job: ConversionJobEntity, isManual: Boolean): Boolean {
        val jobId = job.id
        val now = System.currentTimeMillis()
        
        // 1. Check Stability Period (Skip if manual)
        val eligibilityTime = job.cleanupEligibilityTimestamp ?: return true
        if (!isManual && now < eligibilityTime) {
            val daysLeft = (eligibilityTime - now) / (1000 * 60 * 60 * 24)
            Log.d("AuraCleanupWorker", "[$jobId] Not yet eligible for cleanup. $daysLeft days remaining.")
            dao.update(job.copy(cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name))
            return true
        }

        // 2. Final Safety Gate - Verify Replacement Integrity (Mandatory every run)
        val finalUriStr = job.finalMediaUri ?: return failJob(job, "Final media URI is missing from record.")
        val finalUri = Uri.parse(finalUriStr)
        
        Log.d("AuraCleanupWorker", "[$jobId] Performing pre-cleanup replacement verification.")
        
        if (!uriExists(applicationContext, finalUri)) {
            return failJob(job, "Replacement file missing from storage. Cleanup aborted for safety.", OriginalCleanupStatus.CLEANUP_BLOCKED)
        }

        // Fresh playback validation immediately before deletion
        val playbackOk = AuraPlaybackValidator.validatePlayback(applicationContext, finalUri)
        if (!playbackOk) {
            return failJob(job, "Replacement file failed fresh playback validation. Cleanup aborted for safety.", OriginalCleanupStatus.CLEANUP_BLOCKED)
        }

        // 3. Verify Original Identity and Database Link
        val originalMedia = db.mediaDao().getMediaById(job.mediaId) 
            ?: return failJob(job, "Original media record not found in Aura database.")
            
        if (originalMedia.replacedByMediaId != job.finalMediaId) {
            return failJob(job, "Media identity relationship has changed. Cleanup blocked.", OriginalCleanupStatus.CLEANUP_BLOCKED)
        }

        val sourceUri = Uri.parse(job.sourceUri)
        if (!uriExists(applicationContext, sourceUri)) {
            // Idempotency: Original is already physically gone
            Log.i("AuraCleanupWorker", "[$jobId] Original file already absent. Marking cleanup as complete.")
            dao.update(job.copy(
                cleanupStatus = OriginalCleanupStatus.CLEANUP_COMPLETED.name,
                cleanupCompletedTimestamp = System.currentTimeMillis(),
                status = ConversionJobStatus.CLEANUP_COMPLETED.name
            ))
            return true
        }

        // 4. Physical Deletion (The Destructive Path)
        Log.w("AuraCleanupWorker", "[$jobId] EXECUTING PHYSICAL DELETION of original: $sourceUri")
        
        // Update state to IN_PROGRESS to prevent races
        dao.update(job.copy(
            cleanupStatus = OriginalCleanupStatus.CLEANUP_IN_PROGRESS.name,
            cleanupStartedTimestamp = System.currentTimeMillis()
        ))

        val deleted = deleteUri(applicationContext, sourceUri)
        
        // 5. Deletion Verification
        if (deleted) {
            Log.i("AuraCleanupWorker", "[$jobId] Physical deletion verified.")
            
            // Atomic terminal state update
            dao.update(job.copy(
                cleanupStatus = OriginalCleanupStatus.CLEANUP_COMPLETED.name,
                cleanupCompletedTimestamp = System.currentTimeMillis(),
                status = ConversionJobStatus.CLEANUP_COMPLETED.name,
                updatedTimestamp = System.currentTimeMillis()
            ))
            return true
        } else {
            // Check if it was a permissions issue
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAuthRequired(applicationContext, sourceUri)) {
                failJob(job, "User authorization required for deletion.", OriginalCleanupStatus.CLEANUP_BLOCKED)
            } else {
                failJob(job, "FileSystem/MediaStore reported failure during deletion.")
            }
        }
    }

    private fun isAuthRequired(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null)
            false
        } catch (e: SecurityException) {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun failJob(
        job: ConversionJobEntity, 
        error: String, 
        status: OriginalCleanupStatus = OriginalCleanupStatus.CLEANUP_FAILED
    ): Boolean {
        Log.e("AuraCleanupWorker", "Cleanup failed for job ${job.id}: $error")
        dao.update(job.copy(
            cleanupStatus = status.name,
            lastCleanupError = error,
            cleanupAttemptCount = job.cleanupAttemptCount + 1,
            updatedTimestamp = System.currentTimeMillis()
        ))
        return false
    }

    private fun uriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteUri(context: Context, uri: Uri): Boolean {
        Log.d("AuraMediaReplacement", "Attempting physical deletion of $uri")
        return try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                // Double-verify deletion
                val exists = uriExists(context, uri)
                if (!exists) {
                    Log.i("AuraMediaReplacement", "Physical deletion confirmed for $uri")
                    true
                } else {
                    Log.w("AuraMediaReplacement", "delete() returned > 0 but URI still exists: $uri")
                    false
                }
            } else {
                // If 0 rows deleted, check if it's already gone
                val exists = uriExists(context, uri)
                if (!exists) {
                    Log.i("AuraMediaReplacement", "File already absent, treating as success: $uri")
                    true
                } else {
                    Log.w("AuraMediaReplacement", "delete() returned 0 and URI still exists: $uri")
                    false
                }
            }
        } catch (e: SecurityException) {
            // Check if it's a RecoverableSecurityException (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                Log.w("AuraMediaReplacement", "Deletion requires user authorization for $uri")
            } else {
                Log.e("AuraMediaReplacement", "Security exception during deletion of $uri", e)
            }
            false
        } catch (e: Exception) {
            Log.e("AuraMediaReplacement", "Deletion error for $uri", e)
            false
        }
    }
}

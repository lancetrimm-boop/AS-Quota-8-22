package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.compatibility.AuraPlaybackValidator
import com.example.data.db.AuraDatabase
import com.example.data.db.ConversionJobEntity
import com.example.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages the safe handoff of validated conversion candidates to the final Library.
 */
object AuraMediaReplacementManager {

    private const val TAG = "AuraReplacementManager"

    /**
     * Executes the replacement workflow for a validated conversion job.
     * Hardened for Phase 5 with idempotency and crash recovery.
     */
    suspend fun replaceOriginalWithConverted(
        context: Context,
        job: ConversionJobEntity,
        onStatusUpdate: suspend (ReplacementStage, String?) -> Unit
    ): ReplacementResult = withContext(Dispatchers.IO) {
        val db = AuraDatabase.getInstance(context)
        val mediaId = job.mediaId
        val originalMedia = db.mediaDao().getMediaById(mediaId)

        if (originalMedia == null) {
            return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Original media item not found in DB")
        }

        // Idempotency check 1: Already replaced in library
        if (originalMedia.compatibilityStatus == CompatibilityStatus.REPLACED.name) {
            Log.i(TAG, "Media $mediaId already marked as REPLACED. Ensuring job is synchronized.")
            return@withContext ReplacementResult(
                stage = ReplacementStage.READY_FOR_ORIGINAL_CLEANUP,
                finalMediaUri = job.finalMediaUri ?: originalMedia.uriPath,
                finalMediaId = job.finalMediaId ?: originalMedia.replacedByMediaId
            )
        }

        val convertedFilePath = job.outputPath
        if (convertedFilePath == null || !File(convertedFilePath).exists()) {
            // Check if we already have a final media URI that is valid
            if (job.finalMediaUri != null && job.replacementStage == ReplacementStage.READY_FOR_ORIGINAL_CLEANUP.name) {
                return@withContext ReplacementResult(
                    stage = ReplacementStage.READY_FOR_ORIGINAL_CLEANUP,
                    finalMediaUri = job.finalMediaUri,
                    finalMediaId = job.finalMediaId
                )
            }
            return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Transcoded source file not found")
        }

        var finalUri: Uri? = job.finalMediaUri?.let { Uri.parse(it) }
        
        try {
            // Recovery check: Did we already create the MediaStore entry?
            if (finalUri == null || !uriExists(context, finalUri)) {
                Log.d(TAG, "Creating new MediaStore entry for job ${job.id}")
                onStatusUpdate(ReplacementStage.PREPARING, null)
                finalUri = createFinalMediaStoreEntry(context, originalMedia, job)
                if (finalUri == null) {
                    return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Failed to create MediaStore entry")
                }
                
                // CRITICAL: Persist the new final URI immediately for crash recovery
                onStatusUpdate(ReplacementStage.PREPARING, finalUri.toString())
            } else {
                Log.d(TAG, "Reusing existing final URI for job ${job.id}: $finalUri")
            }

            // Recovery check: Is the file already transferred and verified?
            val currentStage = try { ReplacementStage.valueOf(job.replacementStage ?: "") } catch (_: Exception) { ReplacementStage.NOT_STARTED }
            
            if (currentStage.ordinal < ReplacementStage.VERIFYING.ordinal) {
                Log.d(TAG, "Starting/Resuming byte transfer for job ${job.id}")
                onStatusUpdate(ReplacementStage.HANDOFF_IN_PROGRESS, finalUri.toString())
                // Transfer bytes to final destination
                if (!transferBytes(context, convertedFilePath, finalUri)) {
                    cleanup(context, finalUri)
                    return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Failed to transfer transcoded bytes to final destination")
                }
            }

            if (currentStage.ordinal < ReplacementStage.RECONCILING_LIBRARY.ordinal) {
                Log.d(TAG, "Starting/Resuming final validation for job ${job.id}")
                onStatusUpdate(ReplacementStage.VERIFYING, finalUri.toString())
                
                // 3. Verify final media (Structural + Playback)
                val outputReport = AuraMediaCompatibilityEngine.analyzeMedia(context, finalUri.toString(), "VIDEO")
                if (outputReport.status != CompatibilityStatus.PLAYABLE && outputReport.status != CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE) {
                    Log.e(TAG, "Structural validation failed for $finalUri: ${outputReport.status}")
                    cleanup(context, finalUri)
                    return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Final media structural validation failed: ${outputReport.status}")
                }
                
                val playbackOk = AuraPlaybackValidator.validatePlayback(context, finalUri!!)
                if (!playbackOk) {
                    Log.e(TAG, "Playback validation failed for $finalUri")
                    cleanup(context, finalUri)
                    return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = "Final media playback validation failed")
                }
            }

            if (currentStage.ordinal < ReplacementStage.READY_FOR_ORIGINAL_CLEANUP.ordinal) {
                Log.d(TAG, "Starting/Resuming library reconciliation for job ${job.id}")
                onStatusUpdate(ReplacementStage.RECONCILING_LIBRARY, finalUri.toString())
                
                // 4. Reconcile Library
                val newMediaId = extractIdFromUri(finalUri!!) ?: "local_vid_${System.currentTimeMillis()}"
                reconcileLibrary(db, originalMedia, newMediaId, finalUri.toString(), 
                    AuraMediaCompatibilityEngine.analyzeMedia(context, finalUri.toString(), "VIDEO"))

                onStatusUpdate(ReplacementStage.READY_FOR_ORIGINAL_CLEANUP, finalUri.toString())
                Log.i(TAG, "Replacement successful for job ${job.id}. New Media ID: $newMediaId")
            }
            
            // 5. Cleanup temporary transcoded file (the one in cache)
            try { File(convertedFilePath).delete() } catch (_: Exception) {}

            val eligibilityTimestamp = System.currentTimeMillis() + (ConversionConstants.DEFAULT_CLEANUP_STABILITY_DAYS * 24 * 60 * 60 * 1000L)

            return@withContext ReplacementResult(
                stage = ReplacementStage.READY_FOR_ORIGINAL_CLEANUP,
                finalMediaUri = finalUri.toString(),
                finalMediaId = extractIdFromUri(finalUri!!) ?: job.finalMediaId,
                cleanupEligibilityTimestamp = eligibilityTimestamp
            )

        } catch (e: Exception) {
            Log.e(TAG, "Replacement failed for job ${job.id}", e)
            return@withContext ReplacementResult(ReplacementStage.FAILED, errorMessage = e.message)
        }
    }

    private fun uriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanup(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup URI $uri: ${e.message}")
        }
    }

    private fun createFinalMediaStoreEntry(context: Context, original: MediaEntity, job: ConversionJobEntity): Uri? {
        val contentResolver = context.contentResolver
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val originalName = original.title.takeIf { it.isNotBlank() } ?: job.fileName
        val baseName = originalName.substringBeforeLast(".")
        val finalName = "$baseName.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
                // Try to put it in the same relative path if possible
                val originalUri = Uri.parse(original.uriPath)
                if (originalUri.authority == "media") {
                    // Extracting path from MediaStore is tricky, usually we just let it go to default Movies/Videos
                }
            }
        }

        return contentResolver.insert(videoCollection, values)
    }

    private fun transferBytes(context: Context, sourcePath: String, destUri: Uri): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            val outputStream = context.contentResolver.openOutputStream(destUri) ?: return false
            val inputStream = FileInputStream(sourceFile)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(destUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Byte transfer failed", e)
            false
        }
    }

    private suspend fun reconcileLibrary(
        db: AuraDatabase,
        oldItem: MediaEntity,
        newId: String,
        newUri: String,
        report: com.example.compatibility.MediaCompatibilityReport
    ) {
        val newItem = oldItem.copy(
            id = newId,
            uriPath = newUri,
            imageUrl = newUri,
            sizeBytes = report.sizeBytes,
            durationMs = report.durationMs,
            width = report.width,
            height = report.height,
            compatibilityStatus = CompatibilityStatus.PLAYABLE.name,
            conversionStatus = ConversionStatus.CONVERTED.name,
            convertedUri = "", // The primary URI is now the converted one
            dateModified = System.currentTimeMillis()
        )

        db.withTransaction {
            // 1. Insert new item
            db.mediaDao().insert(newItem)
            
            // 2. Migrate intelligence data
            val oldId = oldItem.id
            db.mediaDao().migratePairwiseA(oldId, newId)
            db.mediaDao().migratePairwiseB(oldId, newId)
            db.mediaDao().migratePairwiseChosen(oldId, newId)
            db.mediaDao().migrateCollectionItems(oldId, newId)
            db.mediaDao().migrateMicroMoments(oldId, newId)
            db.mediaDao().migrateClipInteractions(oldId, newId)
            db.mediaDao().migrateAISkipEvents(oldId, newId)
            db.mediaDao().migratePlaybackErrors(oldId, newId)
            db.mediaDao().migrateConversionJobs(oldId, newId)
            
            // 3. Mark old item as replaced/deleted in Aura
            db.mediaDao().update(oldItem.copy(
                isDeleted = true,
                compatibilityStatus = CompatibilityStatus.REPLACED.name,
                replacedByMediaId = newId
            ))
        }
    }

    private fun extractIdFromUri(uri: Uri): String? {
        return try {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null) "local_vid_$id" else null
        } catch (e: Exception) {
            null
        }
    }
}

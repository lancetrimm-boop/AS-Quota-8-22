package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.db.MediaEntity
import com.example.data.semantic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust background worker for continuous AI embedding generation and enrichment.
 */
class AuraEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AuraEnrichmentWorker"
        private const val BATCH_SIZE = 15 
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<AuraEnrichmentWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.REPLACE, 
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = MediaRepository.getInstance(applicationContext)
        val db = repository.getDatabase() ?: return@withContext Result.retry()
        val mediaDao = db.mediaDao()
        
        mediaDao.recoverStuckEnrichment()

        val textEngine = repository.embeddingProvider
        val visualEngine = repository.mobileCLIPProvider
        val hasVisualReady = visualEngine != null && visualEngine.isReady()

        android.util.Log.d("AuraSemanticTrace", "ENRICHMENT_SESSION_START textEngine=${textEngine?.descriptor?.modelId} visualEngine=${visualEngine?.descriptor?.modelId} visualReady=$hasVisualReady")

        val batch = mediaDao.getNextEnrichmentBatch(BATCH_SIZE)
        if (batch.isEmpty()) {
            Log.d(TAG, "Library enrichment complete or nothing to do.")
            logStats(repository)
            return@withContext Result.success()
        }

        Log.i(TAG, "Executing enrichment batch - Items: ${batch.size}")

        var totalProgressMade = 0

        batch.forEach { entity ->
            if (isStopped) return@forEach

            // AURA WORKER STABILITY: Pre-check if we can actually improve this item
            val currentStatus = try { EnrichmentStatus.valueOf(entity.enrichmentStatus) } catch (e: Exception) { EnrichmentStatus.PENDING }
            
            val canImproveText = currentStatus == EnrichmentStatus.PENDING || 
                                currentStatus == EnrichmentStatus.FAILED_RETRYABLE || 
                                currentStatus == EnrichmentStatus.VISUAL_ONLY
            
            val canImproveVisual = hasVisualReady && (
                                currentStatus == EnrichmentStatus.PENDING || 
                                currentStatus == EnrichmentStatus.FAILED_RETRYABLE || 
                                currentStatus == EnrichmentStatus.TEXT_ONLY
                               )

            if (!canImproveText && !canImproveVisual) {
                // Cannot improve this item right now. Update timestamp so it moves to end of queue.
                mediaDao.updateEnrichmentStatus(entity.id, entity.enrichmentStatus)
                return@forEach
            }

            try {
                // Claim for processing
                mediaDao.updateEnrichmentStatus(entity.id, EnrichmentStatus.PROCESSING.name)
                
                val finalStatus = processItemEnrichment(repository, entity, hasVisualReady)
                
                // Progress is ONLY counted if the status actually changed relative to the BEFORE-PROCESSING state
                if (finalStatus.name != entity.enrichmentStatus) {
                    totalProgressMade++
                }
                
                mediaDao.updateEnrichmentStatus(entity.id, finalStatus.name)
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing item ${entity.id}", e)
                mediaDao.recordEnrichmentFailure(entity.id, EnrichmentStatus.FAILED_RETRYABLE.name)
            }
        }

        Log.i(TAG, "Batch session finished. Processed: ${batch.size}, Progress Items: $totalProgressMade")
        logStats(repository)

        // Only reschedule if we made REAL progress in this batch
        if (!isStopped && totalProgressMade > 0) {
            schedule(applicationContext)
        }

        Result.success()
    }

    private suspend fun processItemEnrichment(
        repository: MediaRepository, 
        entity: MediaEntity,
        hasVisualReady: Boolean
    ): EnrichmentStatus {
        val initialStatus = try { EnrichmentStatus.valueOf(entity.enrichmentStatus) } catch (e: Exception) { EnrichmentStatus.PENDING }
        
        // Modal attempts are independent
        val contentResult = processContentEnrichment(repository, entity)
        val visualResult = if (hasVisualReady) processVisualEnrichment(repository, entity) else ModalityResult.ENGINE_MISSING
        
        // Modal success definition (New OR Previously successful)
        val hasText = contentResult == ModalityResult.SUCCESS || 
                      contentResult == ModalityResult.ALREADY_PRESENT || 
                      initialStatus == EnrichmentStatus.TEXT_ONLY || 
                      initialStatus == EnrichmentStatus.COMPLETE
        
        val hasVisual = visualResult == ModalityResult.SUCCESS || 
                        visualResult == ModalityResult.ALREADY_PRESENT || 
                        initialStatus == EnrichmentStatus.VISUAL_ONLY || 
                        initialStatus == EnrichmentStatus.COMPLETE
        
        val finalStatus = when {
            hasText && hasVisual -> EnrichmentStatus.COMPLETE
            hasText -> EnrichmentStatus.TEXT_ONLY
            hasVisual -> EnrichmentStatus.VISUAL_ONLY
            else -> EnrichmentStatus.FAILED_RETRYABLE
        }

        android.util.Log.d("AuraSemanticTrace", "ENRICHMENT_ITEM mediaId=${entity.id} initial=${entity.enrichmentStatus} final=${finalStatus.name} text=$contentResult visual=$visualResult")
        
        return finalStatus
    }

    private enum class ModalityResult { SUCCESS, FAILURE, ALREADY_PRESENT, ENGINE_MISSING }

    private suspend fun processContentEnrichment(repository: MediaRepository, entity: MediaEntity): ModalityResult {
        val provider = repository.embeddingProvider ?: return ModalityResult.ENGINE_MISSING
        val repo = repository.semanticRepresentationRepository ?: return ModalityResult.FAILURE
        val currentHash = entity.contentHash ?: "v1_${entity.sizeBytes}_${entity.title.hashCode()}"
        val descriptor = provider.descriptor
        
        val existing = repo.getSpecificRepresentation(entity.id, descriptor.primaryType, descriptor)
        if (existing != null && existing.sourceDataHash == currentHash) return ModalityResult.ALREADY_PRESENT

        val input = SemanticInput.Text(entity.title)
        val result = provider.generateEmbedding(entity.id, input, currentHash)
        
        return when (result) {
            is EmbeddingResult.Success -> {
                repo.saveRepresentation(result.representation)
                repository.semanticCandidateRetriever?.onRepresentationAdded(result.representation)
                ModalityResult.SUCCESS
            }
            is EmbeddingResult.Failure -> ModalityResult.FAILURE
        }
    }

    private suspend fun processVisualEnrichment(repository: MediaRepository, entity: MediaEntity): ModalityResult {
        val visualEngine = repository.visualContextEngine
        val repo = repository.semanticRepresentationRepository ?: return ModalityResult.FAILURE
        val provider = repository.mobileCLIPProvider 
        
        if (provider == null || !provider.isReady()) return ModalityResult.ENGINE_MISSING
        
        val descriptor = provider.descriptor
        val existing = repo.getSpecificRepresentation(entity.id, descriptor.primaryType, descriptor)
        if (existing != null) return ModalityResult.ALREADY_PRESENT

        val success = visualEngine.enrichMedia(entity.id, entity.uriPath, entity.durationMs, applicationContext)
        return if (success) ModalityResult.SUCCESS else ModalityResult.FAILURE
    }

    private suspend fun logStats(repository: MediaRepository) {
        val db = repository.getDatabase() ?: return
        val mediaDao = db.mediaDao()
        val total = mediaDao.getEligibleEnrichmentCount()
        val allItems = mediaDao.getAllMediaSync()
        
        val textCount = allItems.count { it.enrichmentStatus == EnrichmentStatus.TEXT_ONLY.name || it.enrichmentStatus == EnrichmentStatus.COMPLETE.name }
        val visualCount = allItems.count { it.enrichmentStatus == EnrichmentStatus.VISUAL_ONLY.name || it.enrichmentStatus == EnrichmentStatus.COMPLETE.name }
        val complete = allItems.count { it.enrichmentStatus == EnrichmentStatus.COMPLETE.name }
        val textOnly = allItems.count { it.enrichmentStatus == EnrichmentStatus.TEXT_ONLY.name }
        val failed = allItems.count { it.enrichmentStatus == EnrichmentStatus.FAILED_RETRYABLE.name || it.enrichmentStatus == EnrichmentStatus.FAILED_PERMANENT.name }
        
        val usableCoverage = if (total > 0) (textCount.toFloat() / total * 100) else 100f
        val coverageStr = String.format(java.util.Locale.US, "%.2f%%", usableCoverage)
        
        android.util.Log.i("AuraSemanticTrace", "EMBEDDING_PROGRESS total=$total text=$textCount visual=$visualCount complete=$complete textOnly=$textOnly usableCoverage=$coverageStr failed=$failed")
    }
}

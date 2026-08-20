package com.example.data.contribution

import android.content.Context
import androidx.work.*
import com.example.data.MediaRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for performing local contribution synchronization (Phase 3A).
 * 
 * ARCHITECTURE PREPARATION:
 * - Operates via [LocalContributionSyncManager].
 * - Supports exponential backoff and retry.
 * - Enforces network and battery constraints.
 */
class ContributionSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = MediaRepository.getInstance(applicationContext).contributionQueueRepository 
            ?: return Result.failure()
        
        // ARCHITECTURE: Integration of Production Gateway
        val gateway = HttpContributionUploadGateway(
            consentManager = repository.consentManager,
            apiService = ContributionNetworkClient.getApiService()
        )
        val syncManager = LocalContributionSyncManager(repository, gateway)

        return try {
            val result = syncManager.performSync(batchSize = 25)
            if (result.success) {
                Result.success()
            } else {
                // Task 5: Existing retry policy handles transient failures
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "AuraContributionSync"

        /**
         * Schedules periodic synchronization.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ContributionSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
        
        /**
         * Triggers an immediate one-time sync.
         */
        fun triggerImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<ContributionSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
                
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

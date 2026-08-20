package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MediaScannerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = MediaRepository.getInstance(applicationContext)
            repository.scanLocalMedia(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

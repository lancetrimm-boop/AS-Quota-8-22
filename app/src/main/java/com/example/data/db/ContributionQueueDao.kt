package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for local encrypted contribution queue operations.
 */
@Dao
interface ContributionQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ContributionQueueEntity): Long

    @Query("SELECT * FROM contribution_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<ContributionQueueEntity>

    @Query("SELECT * FROM contribution_queue WHERE status IN ('PENDING', 'FAILED') AND retryCount < :maxRetries ORDER BY createdAt ASC")
    suspend fun getRetryableEvents(maxRetries: Int): List<ContributionQueueEntity>

    @Query("SELECT * FROM contribution_queue WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getByStatusBounded(status: String, limit: Int): List<ContributionQueueEntity>

    @Query("SELECT * FROM contribution_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<ContributionQueueEntity>

    @Query("SELECT COUNT(*) FROM contribution_queue")
    suspend fun getCount(): Int

    @Query("UPDATE contribution_queue SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatus(ids: List<Long>, status: String)

    @Query("UPDATE contribution_queue SET status = :status, retryCount = retryCount + 1, lastAttemptTimestamp = :timestamp WHERE id IN (:ids)")
    suspend fun updateStatusAndRetry(ids: List<Long>, status: String, timestamp: Long)

    @Query("UPDATE contribution_queue SET status = :status, retryCount = :retryCount, lastAttemptTimestamp = :timestamp WHERE id IN (:ids)")
    suspend fun updateStatusAndMetadata(ids: List<Long>, status: String, retryCount: Int, timestamp: Long)

    @Query("UPDATE contribution_queue SET idempotencyKey = :idempotencyKey WHERE id = :id AND (idempotencyKey IS NULL OR idempotencyKey = '')")
    suspend fun updateIdempotencyKey(id: Long, idempotencyKey: String)

    @Query("SELECT * FROM contribution_queue WHERE idempotencyKey IS NULL OR idempotencyKey = ''")
    suspend fun getRecordsMissingIdempotencyKey(): List<ContributionQueueEntity>

    @Query("DELETE FROM contribution_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM contribution_queue")
    suspend fun clearAll()
}

package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isDeleted = 0 AND compatibilityStatus NOT IN ('CORRUPT', 'UNSUPPORTED', 'DELETED')")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isDeleted = 0 AND compatibilityStatus NOT IN ('CORRUPT', 'UNSUPPORTED', 'DELETED')")
    suspend fun getAllMediaSync(): List<MediaEntity>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaIncludingDeletedSync(): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaById(id: String): MediaEntity?

    @Query("SELECT * FROM media_items WHERE compatibilityStatus = 'ANALYSIS_PENDING'")
    suspend fun getPendingAnalysis(): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaEntity)

    @Update
    suspend fun update(item: MediaEntity)

    @Update
    suspend fun updateAll(items: List<MediaEntity>)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM media_items WHERE id IN ('m1','m2','m3','m4','m5','m6','m7','m8','c1','c2','c3','c4')")
    suspend fun deleteSampleMedia()

    @Query("DELETE FROM media_items WHERE mediaType NOT IN ('PHOTO', 'VIDEO', 'Movie', 'Photo')")
    suspend fun purgeUnsupportedMedia()

    @Query("SELECT * FROM media_items WHERE isDeleted = 0 AND compatibilityStatus NOT IN ('CORRUPT', 'UNSUPPORTED', 'DELETED') AND lastViewedTimestamp IS NOT NULL ORDER BY lastViewedTimestamp DESC LIMIT 100")
    fun getWatchHistory(): Flow<List<MediaEntity>>

    @Query("UPDATE media_items SET isDeleted = 1, replacedByMediaId = :newId WHERE id = :oldId")
    suspend fun markAsReplaced(oldId: String, newId: String)

    @Query("UPDATE pairwise_outcomes SET optionAId = :newId WHERE optionAId = :oldId")
    suspend fun migratePairwiseA(oldId: String, newId: String)

    @Query("UPDATE pairwise_outcomes SET optionBId = :newId WHERE optionBId = :oldId")
    suspend fun migratePairwiseB(oldId: String, newId: String)

    @Query("UPDATE pairwise_outcomes SET chosenId = :newId WHERE chosenId = :oldId")
    suspend fun migratePairwiseChosen(oldId: String, newId: String)

    @Query("UPDATE collection_items SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun migrateCollectionItems(oldId: String, newId: String)

    @Query("UPDATE micro_moments SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun migrateMicroMoments(oldId: String, newId: String)

    @Query("UPDATE clip_interactions SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun migrateClipInteractions(oldId: String, newId: String)

    @Query("UPDATE ai_skip_events SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun migrateAISkipEvents(oldId: String, newId: String)

    @Query("UPDATE playback_error_logs SET mediaItemId = :newId WHERE mediaItemId = :oldId")
    suspend fun migratePlaybackErrors(oldId: String, newId: String)

    @Query("UPDATE conversion_jobs SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun migrateConversionJobs(oldId: String, newId: String)

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getCount(): Int
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchByQuery(query: String)
}


@Dao
interface PairwiseDao {
    @Query("SELECT * FROM pairwise_outcomes ORDER BY timestamp DESC")
    fun getAllOutcomes(): Flow<List<PairwiseOutcomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: PairwiseOutcomeEntity)

    @Query("SELECT COUNT(*) FROM pairwise_outcomes WHERE outcomeType = 'VOTE'")
    suspend fun getVoteCount(): Int
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND mediaId = :mediaId")
    suspend fun removeItem(collectionId: String, mediaId: String)

    @Query("SELECT mediaId FROM collection_items WHERE collectionId = :collectionId")
    suspend fun getMediaIdsForCollection(collectionId: String): List<String>
}

@Dao
interface MicroMomentDao {
    @Insert
    suspend fun insertMoment(moment: MicroMomentEntity)

    @Query("SELECT COUNT(*) FROM micro_moments WHERE mediaId = :mediaId")
    suspend fun getMomentCountForMedia(mediaId: String): Int
}

@Dao
interface ClipInteractionDao {
    @Query("SELECT * FROM clip_interactions ORDER BY (previewCount * 1 + selectCount * 2 + exportCount * 5) DESC")
    fun getAllInteractions(): Flow<List<ClipInteractionEntity>>

    @Query("SELECT * FROM clip_interactions WHERE mediaId = :mediaId AND startTimeMs = :startTimeMs")
    suspend fun getInteraction(mediaId: String, startTimeMs: Long): ClipInteractionEntity?

    @Query("SELECT * FROM clip_interactions ORDER BY (previewCount * 1 + selectCount * 2 + exportCount * 5) DESC LIMIT 5")
    suspend fun getTopEngagedClips(): List<ClipInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(interaction: ClipInteractionEntity)

    @Query("SELECT COALESCE(SUM(previewCount), 0) FROM clip_interactions")
    suspend fun getTotalClipPreviews(): Int

    @Query("SELECT COALESCE(SUM(selectCount), 0) FROM clip_interactions")
    suspend fun getTotalClipSelections(): Int

    @Query("SELECT COALESCE(SUM(exportCount), 0) FROM clip_interactions")
    suspend fun getTotalClipExports(): Int
}

@Dao
interface AISkipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AISkipEventEntity)

    @Query("SELECT * FROM ai_skip_events ORDER BY timestamp DESC")
    fun observeAllEvents(): Flow<List<AISkipEventEntity>>

    @Query("SELECT * FROM ai_skip_events WHERE mediaId = :mediaId ORDER BY timestamp DESC")
    suspend fun getEventsForMedia(mediaId: String): List<AISkipEventEntity>

    @Query("SELECT COUNT(*) FROM ai_skip_events WHERE eventType IN ('SKIP_FORWARD', 'REPEATED_SKIP')")
    suspend fun getTotalSkipForwards(): Int

    @Query("SELECT COUNT(*) FROM ai_skip_events WHERE eventType = 'SKIP_BACK'")
    suspend fun getTotalSkipBacks(): Int

    @Query("SELECT COUNT(*) FROM ai_skip_events WHERE eventType = 'SKIP_REVERSAL'")
    suspend fun getTotalSkipReversals(): Int

    @Query("SELECT COUNT(*) FROM ai_skip_events WHERE eventType = 'WATCHED_DESTINATION'")
    suspend fun getTotalWatchedDestinations(): Int
}

@Dao
interface UserPreferenceDao {
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE `key` = :key")
    suspend fun getPreference(key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_records ORDER BY timestamp DESC")
    fun getAllEvidence(): Flow<List<EvidenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity)

    @Query("DELETE FROM evidence_records WHERE id = :id")
    suspend fun deleteEvidence(id: String)

    @Query("DELETE FROM evidence_records")
    suspend fun clearAll()
}

@Dao
interface TuningAuditDao {
    @Insert
    suspend fun insertAudit(audit: TuningAuditEntity)

    @Query("SELECT * FROM tuning_audits ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAudits(): Flow<List<TuningAuditEntity>>
}

@Dao
interface RejectedMediaDao {
    @Query("SELECT * FROM rejected_media ORDER BY timestampRejected DESC")
    fun getAllRejectedMedia(): Flow<List<RejectedMediaEntity>>

    @Query("SELECT * FROM rejected_media")
    suspend fun getAllRejectedMediaSync(): List<RejectedMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RejectedMediaEntity)

    @Query("DELETE FROM rejected_media WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CreatorDao {
    @Query("SELECT * FROM creator_profiles")
    fun getAllCreators(): Flow<List<CreatorEntity>>

    @Query("SELECT * FROM creator_profiles WHERE id = :id")
    suspend fun getCreatorById(id: String): CreatorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(creator: CreatorEntity)

    @Update
    suspend fun update(creator: CreatorEntity)
}

@Dao
interface PlaybackErrorLogDao {
    @Insert
    suspend fun insert(error: PlaybackErrorLogEntity)

    @Update
    suspend fun update(error: PlaybackErrorLogEntity)

    @Query("SELECT * FROM playback_error_logs WHERE mediaItemId = :mediaItemId AND errorCode = :errorCode AND exceptionClass = :exceptionClass AND sessionId = :sessionId LIMIT 1")
    suspend fun findExistingError(mediaItemId: String?, errorCode: Int?, exceptionClass: String?, sessionId: String?): PlaybackErrorLogEntity?

    @Query("SELECT * FROM playback_error_logs ORDER BY timestamp DESC")
    fun observeRecentErrors(): Flow<List<PlaybackErrorLogEntity>>

    @Query("SELECT * FROM playback_error_logs WHERE mediaItemId = :mediaItemId ORDER BY timestamp DESC")
    fun getErrorsForMedia(mediaItemId: String): Flow<List<PlaybackErrorLogEntity>>

    @Query("SELECT * FROM playback_error_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getErrorsForSession(sessionId: String): Flow<List<PlaybackErrorLogEntity>>

    @Query("DELETE FROM playback_error_logs WHERE id = :errorId")
    suspend fun delete(errorId: Long)

    @Query("DELETE FROM playback_error_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM playback_error_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentErrorsBounded(limit: Int): Flow<List<PlaybackErrorLogEntity>>
    
    @Query("DELETE FROM playback_error_logs WHERE id NOT IN (SELECT id FROM playback_error_logs ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimLog(limit: Int)
}

@Dao
interface ConversionJobDao {
    @Insert
    suspend fun insert(job: ConversionJobEntity): Long

    @Insert
    suspend fun insertAll(jobs: List<ConversionJobEntity>)

    @Update
    suspend fun update(job: ConversionJobEntity)

    @Query("SELECT * FROM conversion_jobs ORDER BY createdTimestamp DESC")
    fun observeAllJobs(): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM conversion_jobs WHERE status IN ('QUEUED', 'PREPARING', 'CONVERTING', 'VALIDATING', 'TESTING_PLAYBACK') ORDER BY createdTimestamp ASC")
    fun observeActiveJobs(): Flow<List<ConversionJobEntity>>

    @Query("SELECT * FROM conversion_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: Long): ConversionJobEntity?

    @Query("UPDATE conversion_jobs SET status = :status, updatedTimestamp = :timestamp WHERE id = :jobId")
    suspend fun updateStatus(jobId: Long, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversion_jobs SET progress = :progress, updatedTimestamp = :timestamp WHERE id = :jobId")
    suspend fun updateProgress(jobId: Long, progress: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversion_jobs WHERE id = :jobId")
    suspend fun delete(jobId: Long)

    @Query("DELETE FROM conversion_jobs WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("UPDATE conversion_jobs SET status = 'CANCELLED' WHERE status = 'QUEUED'")
    suspend fun cancelAllQueued()

    @Query("SELECT * FROM conversion_jobs WHERE cleanupStatus = 'WAITING_FOR_STABILITY' OR cleanupStatus = 'CLEANUP_ELIGIBLE'")
    suspend fun getCleanupPendingJobs(): List<ConversionJobEntity>
}


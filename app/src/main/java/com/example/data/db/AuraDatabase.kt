package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DatabaseSecurityException(message: String, cause: Throwable) : Exception(message, cause)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN sizeBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN lastViewedTimestamp INTEGER DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN compatibilityStatus TEXT NOT NULL DEFAULT 'PLAYABLE'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN containerFormat TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN videoCodec TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN audioCodec TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN compatibilityReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN conversionStatus TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN convertedUri TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN lastCompatibilityCheckTimestamp INTEGER DEFAULT NULL")
    }
}

val MIGRATION_3_6 = object : Migration(3, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Bridge migration for historical development version 3.
        // Schema was consistent between 3 and 6 except for additions in 7+.
    }
}

val MIGRATION_4_6 = object : Migration(4, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Bridge migration for historical development version 4.
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Bridge migration for historical development version 5.
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `evidence_records` (
                `id` TEXT NOT NULL, 
                `tier` TEXT NOT NULL, 
                `sampleCount` INTEGER NOT NULL, 
                `score` REAL NOT NULL, 
                `quality` REAL NOT NULL, 
                `source` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN eloRating REAL NOT NULL DEFAULT 1500.0")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN preRatingA REAL NOT NULL DEFAULT 1500.0")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN preRatingB REAL NOT NULL DEFAULT 1500.0")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN postRatingA REAL NOT NULL DEFAULT 1500.0")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN postRatingB REAL NOT NULL DEFAULT 1500.0")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN expectedScoreA REAL NOT NULL DEFAULT 0.5")
        db.execSQL("ALTER TABLE pairwise_outcomes ADD COLUMN kFactor REAL NOT NULL DEFAULT 32.0")
        // Note: added associatedManifestId here too for safety on other devices
        db.execSQL("ALTER TABLE evidence_records ADD COLUMN associatedManifestId TEXT DEFAULT NULL")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recovery migration for missing column in some version 8 dev builds
        try {
            db.execSQL("ALTER TABLE evidence_records ADD COLUMN associatedManifestId TEXT DEFAULT NULL")
        } catch (e: Exception) {
            // Might already exist if migration 7_8 ran correctly on this device
        }
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tuning_audits` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `preferenceKey` TEXT NOT NULL, 
                `previousEffectiveValue` REAL NOT NULL, 
                `newEffectiveValue` REAL NOT NULL, 
                `userBaselineAtTime` REAL NOT NULL, 
                `aiAdjustmentAtTime` REAL NOT NULL, 
                `evidenceCategory` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL, 
                `isUserGenerated` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `rejected_media` (
                `id` TEXT NOT NULL, 
                `uriPath` TEXT NOT NULL, 
                `title` TEXT NOT NULL, 
                `mediaType` TEXT NOT NULL, 
                `reason` TEXT NOT NULL, 
                `compatibilityStatus` TEXT NOT NULL, 
                `containerFormat` TEXT NOT NULL DEFAULT '', 
                `videoCodec` TEXT NOT NULL DEFAULT '', 
                `audioCodec` TEXT NOT NULL DEFAULT '', 
                `timestampRejected` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN lastExposedTimestamp INTEGER DEFAULT NULL") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN contentHash TEXT DEFAULT NULL") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN parentContentId TEXT DEFAULT NULL") } catch (e: Exception) {}
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Adding columns WITHOUT explicit DEFAULT NULL to match 'undefined' expectation in Room
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN selectionReason TEXT") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN creatorId TEXT") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN creatorName TEXT") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN sourcePlatform TEXT") } catch (e: Exception) {}
        
        // Correcting schema mismatch for behavioral tracking fields
        // These expect 'undefined' default value in Room schema matching (no explicit DEFAULT in SQL)
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
        try { db.execSQL("ALTER TABLE media_items ADD COLUMN exposureCount INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `creator_profiles` (
                `id` TEXT NOT NULL, 
                `name` TEXT NOT NULL, 
                `platform` TEXT NOT NULL, 
                `affinityScore` REAL NOT NULL DEFAULT 0.0, 
                `interactionCount` INTEGER NOT NULL DEFAULT 0, 
                `lastInteractionTimestamp` INTEGER NOT NULL DEFAULT 0, 
                `topMoodTagsJson` TEXT NOT NULL DEFAULT '', 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

@Database(
    entities = [
        MediaEntity::class,
        PairwiseOutcomeEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        MicroMomentEntity::class,
        UserPreferenceEntity::class,
        ClipInteractionEntity::class,
        AISkipEventEntity::class,
        EvidenceEntity::class,
        TuningAuditEntity::class,
        RejectedMediaEntity::class,
        CreatorEntity::class,
        FindingEntity::class,
        SuggestedImprovementEntity::class,
        LifecycleEventEntity::class,
        IntelligenceActionEntity::class,
        BlueprintArtifactEntity::class,
        ImplementationRunEntity::class,
        VerificationResultEntity::class,
        MonitoringSessionEntity::class,
        ValidationResultEntity::class,
        RegressionAlertEntity::class,
        RollbackRunEntity::class,
        ReviewMetadataEntity::class,
        UserCheckpointEntity::class,
        IntegrityAuditEntity::class,
        EvidenceSnapshotEntity::class,
        IntelligenceEventEntity::class,
        AttentionItemEntity::class,
        SavedIntelligenceReportEntity::class,
        ContributionQueueEntity::class,
        SearchHistoryEntity::class,
        PlaybackErrorLogEntity::class,
        ConversionJobEntity::class,
        SemanticRepresentationEntity::class
    ],
    version = 39,
    exportSchema = false
)
@androidx.room.TypeConverters(IntelligenceConverters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun pairwiseDao(): PairwiseDao
    abstract fun collectionDao(): CollectionDao
    abstract fun microMomentDao(): MicroMomentDao
    abstract fun clipInteractionDao(): ClipInteractionDao
    abstract fun aiSkipDao(): AISkipDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun tuningAuditDao(): TuningAuditDao
    abstract fun rejectedMediaDao(): RejectedMediaDao
    abstract fun creatorDao(): CreatorDao
    abstract fun intelligenceDao(): IntelligenceDao
    abstract fun contributionQueueDao(): ContributionQueueDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playbackErrorLogDao(): PlaybackErrorLogDao
    abstract fun conversionJobDao(): ConversionJobDao
    abstract fun semanticRepresentationDao(): SemanticRepresentationDao


    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        /**
         * Clears the current database singleton.
         * Used during reset/quarantine operations to ensure a fresh session.
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `intelligence_findings` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, `classification` TEXT NOT NULL, `confidence` TEXT NOT NULL, `dateDiscovered` INTEGER NOT NULL, `technicalDetailsJson` TEXT NOT NULL, `lifecycleState` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `suggested_improvements` (`id` TEXT NOT NULL, `findingId` TEXT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, `priority` TEXT NOT NULL, `expectedImpact` TEXT NOT NULL, `risk` TEXT NOT NULL, `confidence` TEXT NOT NULL, `evidenceCount` INTEGER NOT NULL, `source` TEXT NOT NULL, `classification` TEXT NOT NULL, `rationale` TEXT NOT NULL, `whatWillChange` TEXT NOT NULL, `whatWillNotChange` TEXT NOT NULL, `proposedChangesJson` TEXT NOT NULL, `implementationPlanJson` TEXT NOT NULL, `verificationPlanJson` TEXT NOT NULL, `rollbackPlanJson` TEXT NOT NULL, `blueprintArtifactId` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `intelligence_lifecycle_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `targetId` TEXT NOT NULL, `fromState` TEXT, `toState` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `actor` TEXT NOT NULL, `reason` TEXT, `metadataJson` TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `intelligence_actions` (`id` TEXT NOT NULL, `improvementId` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `planJson` TEXT NOT NULL, `resultJson` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `blueprint_artifacts` (`id` TEXT NOT NULL, `blueprintId` TEXT NOT NULL, `version` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `suggested_improvements` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `blueprint_artifacts` ADD COLUMN `improvementId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `blueprint_artifacts` ADD COLUMN `proposalVersion` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add missing column to suggested_improvements if needed
                try {
                    db.execSQL("ALTER TABLE `suggested_improvements` ADD COLUMN `technicalDetailsJson` TEXT")
                } catch (e: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `implementation_runs` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `artifactId` TEXT NOT NULL, 
                        `proposalVersion` INTEGER NOT NULL, 
                        `startTime` INTEGER NOT NULL, 
                        `endTime` INTEGER, 
                        `status` TEXT NOT NULL, 
                        `notes` TEXT, 
                        `changedFilesJson` TEXT, 
                        `deviationDetected` INTEGER NOT NULL, 
                        `deviationDetails` TEXT, 
                        `resultSummary` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `verification_results` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `runId` TEXT NOT NULL, 
                        `artifactId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `buildPassed` INTEGER NOT NULL, 
                        `testsPassed` INTEGER NOT NULL, 
                        `regressionPassed` INTEGER NOT NULL, 
                        `dbIntegrityPassed` INTEGER NOT NULL, 
                        `scopeCompliant` INTEGER NOT NULL, 
                        `acceptanceCriteriaResultsJson` TEXT NOT NULL, 
                        `technicalDetailsJson` TEXT, 
                        `overallPassed` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `monitoring_sessions` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `runId` TEXT NOT NULL, 
                        `artifactId` TEXT NOT NULL, 
                        `startTime` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `baselineMetricsJson` TEXT NOT NULL, 
                        `currentMetricsJson` TEXT NOT NULL, 
                        `requiredSampleCount` INTEGER NOT NULL, 
                        `currentSampleCount` INTEGER NOT NULL, 
                        `durationDays` INTEGER NOT NULL, 
                        `regressionDetected` INTEGER NOT NULL, 
                        `confidence` REAL NOT NULL, 
                        `validationOutcome` TEXT, 
                        `evidenceIdsJson` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `validation_results_history` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `sessionId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `outcome` TEXT NOT NULL, 
                        `evidenceSummary` TEXT NOT NULL, 
                        `baselineValue` REAL NOT NULL, 
                        `finalValue` REAL NOT NULL, 
                        `change` REAL NOT NULL, 
                        `sampleCount` INTEGER NOT NULL, 
                        `confidence` REAL NOT NULL, 
                        `regressionSeverity` TEXT, 
                        `metadataJson` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `regression_alerts` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `runId` TEXT NOT NULL, 
                        `artifactId` TEXT NOT NULL, 
                        `sessionId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `severity` TEXT NOT NULL, 
                        `affectedMetric` TEXT NOT NULL, 
                        `baselineValue` REAL NOT NULL, 
                        `preRegressionValue` REAL NOT NULL, 
                        `currentResult` REAL NOT NULL, 
                        `change` REAL NOT NULL, 
                        `evidenceIdsJson` TEXT NOT NULL, 
                        `confidence` REAL NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `recommendation` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rollback_runs` (
                        `id` TEXT NOT NULL, 
                        `improvementId` TEXT NOT NULL, 
                        `regressionId` TEXT NOT NULL, 
                        `originalRunId` TEXT NOT NULL, 
                        `artifactId` TEXT NOT NULL, 
                        `startTime` INTEGER NOT NULL, 
                        `endTime` INTEGER, 
                        `status` TEXT NOT NULL, 
                        `notes` TEXT, 
                        `changedFilesJson` TEXT, 
                        `deviationDetected` INTEGER NOT NULL, 
                        `resultSummary` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `review_metadata` (
                        `targetId` TEXT NOT NULL, 
                        `firstSeenTimestamp` INTEGER, 
                        `lastSeenTimestamp` INTEGER, 
                        `reviewedTimestamp` INTEGER, 
                        `status` TEXT NOT NULL, 
                        PRIMARY KEY(`targetId`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_checkpoints` (
                        `checkpointId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`checkpointId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `integrity_audit_history` (
                        `id` TEXT NOT NULL, 
                        `targetId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `scope` TEXT NOT NULL, 
                        `issuesJson` TEXT NOT NULL, 
                        `recommendedAction` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `evidence_snapshots` (
                        `improvementId` TEXT NOT NULL, 
                        `blueprintId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `reportJson` TEXT NOT NULL, 
                        PRIMARY KEY(`improvementId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `intelligence_events` (
                        `id` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `sourceId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `failureReason` TEXT, 
                        `retryCount` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attention_items` (
                        `id` TEXT NOT NULL, 
                        `sourceType` TEXT NOT NULL, 
                        `sourceId` TEXT NOT NULL, 
                        `attentionType` TEXT NOT NULL, 
                        `priority` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `summary` TEXT NOT NULL, 
                        `whyItMatters` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `resolvedAt` INTEGER, 
                        `requiresAction` INTEGER NOT NULL, 
                        `deepLink` TEXT NOT NULL, 
                        `deduplicationKey` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `saved_intelligence_reports` (
                        `id` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `dataThrough` INTEGER NOT NULL, 
                        `reportingPeriodDays` INTEGER NOT NULL, 
                        `reportJson` TEXT NOT NULL, 
                        `isSnapshot` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contribution_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `eventType` TEXT NOT NULL, 
                        `schemaVersion` TEXT NOT NULL, 
                        `payloadJson` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contribution_queue` ADD COLUMN `idempotencyKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `contribution_queue` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `contribution_queue` ADD COLUMN `lastAttemptTimestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `contribution_queue` SET `status` = 'QUEUED' WHERE `status` = 'PENDING'")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Aura Phase 3A: Recommended status normalization
                db.execSQL("UPDATE `contribution_queue` SET `status` = 'PENDING' WHERE `status` = 'QUEUED' OR `status` = 'PENDING'")
                db.execSQL("UPDATE `contribution_queue` SET `status` = 'PROCESSING' WHERE `status` = 'UPLOADING' OR `status` = 'PROCESSING'")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Remove duplicate idempotencyKey records, keeping the oldest (min ID)
                // Note: We only target non-empty keys for duplicate removal.
                db.execSQL("""
                    DELETE FROM contribution_queue 
                    WHERE id NOT IN (
                        SELECT MIN(id) 
                        FROM contribution_queue 
                        GROUP BY idempotencyKey
                    ) AND idempotencyKey IS NOT NULL AND idempotencyKey != ''
                """.trimIndent())

                // 2. Create the unique index
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contribution_queue_idempotencyKey` ON `contribution_queue` (`idempotencyKey`)")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `rejected_media` ADD COLUMN `contentHash` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_isDeleted_compatibilityStatus` ON `media_items` (`isDeleted`, `compatibilityStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_dateAdded` ON `media_items` (`dateAdded`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_contentHash` ON `media_items` (`contentHash`)")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_error_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `mediaItemId` TEXT, 
                        `mediaUri` TEXT, 
                        `mediaTitle` TEXT, 
                        `fileName` TEXT, 
                        `mimeType` TEXT, 
                        `durationMs` INTEGER, 
                        `playbackPositionMs` INTEGER, 
                        `playbackState` TEXT, 
                        `playWhenReady` INTEGER, 
                        `errorCode` INTEGER, 
                        `errorCodeName` TEXT, 
                        `errorMessage` TEXT, 
                        `exceptionClass` TEXT, 
                        `causeChain` TEXT, 
                        `stackTrace` TEXT, 
                        `rendererName` TEXT, 
                        `rendererIndex` INTEGER, 
                        `codecName` TEXT, 
                        `codecMimeType` TEXT, 
                        `deviceManufacturer` TEXT, 
                        `deviceModel` TEXT, 
                        `androidVersion` TEXT, 
                        `sdkInt` INTEGER, 
                        `appVersion` TEXT, 
                        `media3Version` TEXT, 
                        `networkState` TEXT, 
                        `isLocalFile` INTEGER NOT NULL, 
                        `sessionId` TEXT, 
                        `recoveryAttempted` INTEGER NOT NULL DEFAULT 0, 
                        `recoverySuccessful` INTEGER, 
                        `diagnosticSummary` TEXT
                    )
                """.trimIndent())
            }
        }


        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playback_error_logs` ADD COLUMN `occurrenceCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `playback_error_logs` ADD COLUMN `lastOccurrenceTimestamp` INTEGER NOT NULL DEFAULT 0")
                // Initialize lastOccurrenceTimestamp with existing timestamp for consistency
                db.execSQL("UPDATE `playback_error_logs` SET `lastOccurrenceTimestamp` = `timestamp`")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversion_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `mediaId` TEXT NOT NULL, 
                        `sourceUri` TEXT NOT NULL, 
                        `fileName` TEXT NOT NULL, 
                        `mediaTitle` TEXT, 
                        `sourceSize` INTEGER NOT NULL, 
                        `sourceDurationMs` INTEGER NOT NULL, 
                        `sourceVideoCodec` TEXT, 
                        `sourceAudioCodec` TEXT, 
                        `targetContainer` TEXT NOT NULL, 
                        `targetVideoCodec` TEXT NOT NULL, 
                        `targetAudioCodec` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `progress` INTEGER NOT NULL, 
                        `attemptCount` INTEGER NOT NULL, 
                        `createdTimestamp` INTEGER NOT NULL, 
                        `startedTimestamp` INTEGER, 
                        `completedTimestamp` INTEGER, 
                        `updatedTimestamp` INTEGER NOT NULL, 
                        `outputPath` TEXT, 
                        `failureStage` TEXT, 
                        `errorMessage` TEXT, 
                        `realTimeFactor` REAL NOT NULL, 
                        `compressionRatio` REAL NOT NULL, 
                        `workRequestId` TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversion_jobs_mediaId` ON `conversion_jobs` (`mediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversion_jobs_status` ON `conversion_jobs` (`status`)")
            }
        }


        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `replacedByMediaId` TEXT")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `finalMediaUri` TEXT")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `finalMediaId` TEXT")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `replacementStage` TEXT")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `cleanupStatus` TEXT NOT NULL DEFAULT 'NOT_ELIGIBLE'")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `cleanupEligibilityTimestamp` INTEGER")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `cleanupStartedTimestamp` INTEGER")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `cleanupCompletedTimestamp` INTEGER")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `cleanupAttemptCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `conversion_jobs` ADD COLUMN `lastCleanupError` TEXT")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `semantic_representations` (
                        `id` TEXT NOT NULL, 
                        `mediaId` TEXT NOT NULL, 
                        `representationType` TEXT NOT NULL, 
                        `modelId` TEXT NOT NULL, 
                        `modelVersion` INTEGER NOT NULL, 
                        `dimensionality` INTEGER NOT NULL, 
                        `vectorData` BLOB NOT NULL, 
                        `isNormalized` INTEGER NOT NULL, 
                        `sourceDataHash` TEXT NOT NULL, 
                        `confidence` REAL NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_representations_mediaId` ON `semantic_representations` (`mediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_representations_representationType` ON `semantic_representations` (`representationType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_representations_modelId_modelVersion` ON `semantic_representations` (`modelId`, `modelVersion`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_representations_representationType_modelId_modelVersion` ON `semantic_representations` (`representationType`, `modelId`, `modelVersion`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_semantic_representations_mediaId_representationType_modelId_modelVersion` ON `semantic_representations` (`mediaId`, `representationType`, `modelId`, `modelVersion`)")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `enrichmentStatus` TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `lastEnrichmentAttemptTimestamp` INTEGER")
                db.execSQL("ALTER TABLE `media_items` ADD COLUMN `enrichmentFailureCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_enrichmentStatus` ON `media_items` (`enrichmentStatus`)")
            }
        }


        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                // Return instance if created while waiting for lock
                INSTANCE?.let { return it }

                try {
                    // 1. Ensure SQLCipher libraries are loaded (centralized)
                    SQLCipherInitializer.initialize(context)
                    
                    // 2. Retrieve the persistent passphrase in hex format for SQLCipher raw usage
                    val hexKey = PassphraseManager.getPassphraseAsHex(context)
                    
                    // 3. Build Room with encryption SupportOpenHelperFactory using the raw hex key
                    val factory = SupportOpenHelperFactory(hexKey.toByteArray())
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AuraDatabase::class.java,
                        "aura_intelligence.db"
                    )
                    .openHelperFactory(factory)
                    .addMigrations(
                        MIGRATION_1_2, 
                        MIGRATION_2_3, 
                        MIGRATION_3_6,
                        MIGRATION_4_6,
                        MIGRATION_5_6,
                        MIGRATION_6_7, 
                        MIGRATION_7_8, 
                        MIGRATION_8_9, 
                        MIGRATION_9_10, 
                        MIGRATION_10_11, 
                        MIGRATION_11_12, 
                        MIGRATION_12_13, 
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25,
                        MIGRATION_25_26,
                        MIGRATION_26_27,
                        MIGRATION_27_28,
                        MIGRATION_28_29,
                        MIGRATION_29_30,
                        MIGRATION_30_31,
                        MIGRATION_31_32, 
                        MIGRATION_32_33,
                        MIGRATION_33_34,
                        MIGRATION_34_35,
                        MIGRATION_35_36,
                        MIGRATION_36_37,
                        MIGRATION_37_38,
                        MIGRATION_38_39
                    )
                    .build()
                    INSTANCE = instance
                    instance
                } catch (e: SecureStorageException) {
                    android.util.Log.e("AuraDatabase", "TERMINAL ERROR: Secure storage failure", e)
                    throw e // Re-throw to be handled by MediaRepository
                } catch (e: Exception) {
                    android.util.Log.e("AuraDatabase", "TERMINAL ERROR: Secure initialization failed", e)
                    // Wrap encryption/transition failures to distinguish from standard SQL errors
                    throw DatabaseSecurityException("Failed to initialize secure database session.", e)
                }
            }
        }
    }
}


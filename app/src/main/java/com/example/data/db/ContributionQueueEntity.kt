package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local encrypted database entity for staging sanitized contribution events.
 *
 * PRIVACY BOUNDARY ENFORCEMENT:
 * - Stores ONLY sanitized Phase 1 contribution JSON payloads.
 * - Stores NO raw media IDs, titles, filenames, URIs, file paths, or user identifiers.
 */
@Entity(
    tableName = "contribution_queue",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class ContributionQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventType: String,
    val schemaVersion: String,
    val payloadJson: String,
    val createdAt: Long,
    val status: String = "PENDING",
    val idempotencyKey: String = "",
    val retryCount: Int = 0,
    val lastAttemptTimestamp: Long = 0L
)

package com.example.data.cleanup

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.db.RejectedMediaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DeletionState {
    IDLE,
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}

class SafeDeleteManager(
    private val repository: MediaRepository
) {
    private val _deletionState = MutableStateFlow(DeletionState.IDLE)
    val deletionState: StateFlow<DeletionState> = _deletionState.asStateFlow()

    private var pendingItems: List<MediaItem> = emptyList()
    private var pendingRecommendations: List<CleanupRecommendation> = emptyList()

    /**
     * Initiates the deletion workflow for a set of items.
     * @return The IntentSenderRequest to be launched by the UI, or null if direct delete was possible.
     */
    fun requestDeletion(
        context: Context,
        items: List<MediaItem>,
        recommendations: List<CleanupRecommendation>,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (items.isEmpty()) return

        pendingItems = items
        pendingRecommendations = recommendations
        _deletionState.value = DeletionState.PENDING

        // Filter for MediaStore compatible URIs to prevent IllegalArgumentException crashes
        val mediaStoreUris = items.mapNotNull { item ->
            try {
                val uri = Uri.parse(item.uriPath)
                if (uri.authority == "media") uri else null
            } catch (e: Exception) {
                null
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreUris.isNotEmpty()) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                launcher.launch(request)
            } catch (e: Exception) {
                android.util.Log.e("SafeDeleteManager", "Failed to create delete request", e)
                _deletionState.value = DeletionState.FAILED
                pendingItems = emptyList()
                pendingRecommendations = emptyList()
            }
        } else if (mediaStoreUris.isEmpty() && items.isNotEmpty()) {
            // All selected items are non-MediaStore (e.g. manually imported file:// or custom content://)
            // For safety and consistency with system dialog, we'll treat this as direct confirmation 
            // of DB removal, as we can't reliably trigger a system dialog for mixed/custom URIs here.
            _deletionState.value = DeletionState.CONFIRMED
            reconcileDatabaseAfterDeletion()
        } else {
            // Fallback for older versions or empty valid URI list
            _deletionState.value = DeletionState.FAILED
            pendingItems = emptyList()
            pendingRecommendations = emptyList()
        }
    }

    /**
     * Processes the result of the system deletion dialog.
     */
    fun handleDeletionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            _deletionState.value = DeletionState.CONFIRMED
            reconcileDatabaseAfterDeletion()
        } else {
            _deletionState.value = DeletionState.CANCELLED
            pendingItems = emptyList()
            pendingRecommendations = emptyList()
        }
    }

    private fun reconcileDatabaseAfterDeletion() {
        val itemsToDelete = pendingItems
        val recs = pendingRecommendations
        
        itemsToDelete.forEach { item ->
            // 1. Add to Rejected Media to prevent re-import
            val recommendation = recs.find { it.mediaId == item.id }
            val rejected = RejectedMediaEntity(
                id = "del_${item.id}_${System.currentTimeMillis()}",
                uriPath = item.uriPath,
                title = item.title,
                mediaType = item.mediaType,
                reason = recommendation?.category?.name ?: "User Deleted",
                compatibilityStatus = item.compatibilityStatus.name,
                containerFormat = item.containerFormat,
                videoCodec = item.videoCodec,
                audioCodec = item.audioCodec,
                contentHash = item.contentHash,
                timestampRejected = System.currentTimeMillis()
            )
            repository.addRejectedMedia(rejected)

            // 2. Remove from MediaRepository and DB
            repository.deleteMediaItem(item.id)
            
            // 3. Clear thumbnail cache
            com.example.util.MediaThumbnailFetcher.removeThumbnail(item.uriPath)
            if (item.imageUrl.isNotEmpty()) {
                com.example.util.MediaThumbnailFetcher.removeThumbnail(item.imageUrl)
            }
            
            // 4. Record learning signal
            repository.recordCleanupSignal(
                mediaId = item.id,
                category = recommendation?.category?.name ?: "NONE",
                score = recommendation?.keepScore ?: 0f,
                isDelete = true
            )
        }

        pendingItems = emptyList()
        pendingRecommendations = emptyList()
    }

    /**
     * Marks an item as 'Kept' by the user, protecting it from future cleanup targeting.
     */
    fun markAsKept(item: MediaItem, recommendation: CleanupRecommendation) {
        // Protect the item by favoriting it
        if (!item.isFavorite) {
            repository.toggleFavorite(item.id)
        }
        
        repository.recordCleanupSignal(
            mediaId = item.id,
            category = recommendation.category.name,
            score = recommendation.keepScore,
            isDelete = false
        )
    }
}

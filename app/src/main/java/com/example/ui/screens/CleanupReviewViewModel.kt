package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.cleanup.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CleanupReviewUiState(
    val recommendations: List<CleanupRecommendation> = emptyList(),
    val filteredRecommendations: List<CleanupRecommendation> = emptyList(),
    val mediaItems: Map<String, MediaItem> = emptyMap(),
    val selectedCategory: CleanupCategory = CleanupCategory.FORGOTTEN,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val storageRecoveryEstimate: Long = 0L,
    val categoryStats: Map<CleanupCategory, CategoryStat> = emptyMap(),
    val currentSort: ReviewSort = ReviewSort.LOWEST_KEEP_SCORE
)

data class CategoryStat(
    val count: Int,
    val storageBytes: Long,
    val averageKeepScore: Float
)

enum class ReviewSort {
    LOWEST_KEEP_SCORE,
    HIGHEST_CONFIDENCE,
    LARGEST_STORAGE,
    MOST_EXPOSED
}

class CleanupReviewViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    val deleteManager = repository.safeDeleteManager

    private val _uiState = MutableStateFlow(CleanupReviewUiState())
    val uiState: StateFlow<CleanupReviewUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
        
        viewModelScope.launch {
            deleteManager.deletionState.collect { state ->
                if (state == DeletionState.CONFIRMED || state == DeletionState.CANCELLED || state == DeletionState.FAILED) {
                    _uiState.update { it.copy(isDeleting = false) }
                    if (state == DeletionState.CONFIRMED) {
                        loadRecommendations() // Refresh
                    }
                } else if (state == DeletionState.PENDING) {
                    _uiState.update { it.copy(isDeleting = true) }
                }
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val allItems = repository.mediaItems.value
            val tasteDNA = repository.tasteDNA.value
            val stats = repository.intelligenceStats.value
            
            // Perform heavy calculation in background
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // 1. Generate Keep Scores
                val keepScoreResults = allItems.map { item ->
                    val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats)
                    KeepScoreEngine.calculateScore(
                        KeepScoreInput(
                            mediaId = item.id,
                            fileSize = item.sizeBytes,
                            dateAdded = item.dateAdded,
                            exposureCount = item.exposureCount,
                            lastExposedTimestamp = item.lastExposedTimestamp,
                            viewCount = item.viewCount,
                            playCount = item.viewCount,
                            averageWatchDuration = if (item.viewCount > 0) 15f else 0f,
                            completionPercentage = item.progress,
                            skipCount = 0,
                            rating = item.rating,
                            isFavorite = item.isFavorite,
                            tasteAlignmentScore = evidence.exploitationScore,
                            contentHash = item.contentHash
                        )
                    )
                }

                // 2. Generate Recommendations
                val metadataMap = allItems.associate { item ->
                    item.id to CleanupItemMetadata(
                        mediaId = item.id,
                        sizeBytes = item.sizeBytes,
                        exposureCount = item.exposureCount,
                        viewCount = item.viewCount,
                        mediaType = item.mediaType,
                        contentHash = item.contentHash,
                        isFavorite = item.isFavorite
                    )
                }

                val recommendations = CleanupRecommendationEngine.generateRecommendations(
                    keepScoreResults,
                    metadataMap
                )

                // 3. Calculate Category Stats
                val statsMap = CleanupCategory.entries.associateWith { cat ->
                    val catRecs = recommendations.filter { it.category == cat }
                    CategoryStat(
                        count = catRecs.size,
                        storageBytes = catRecs.sumOf { it.storageSize },
                        averageKeepScore = if (catRecs.isNotEmpty()) catRecs.map { it.keepScore }.average().toFloat() else 0f
                    )
                }

                _uiState.update { state ->
                    val newState = state.copy(
                        recommendations = recommendations,
                        mediaItems = allItems.associateBy { it.id },
                        categoryStats = statsMap,
                        isLoading = false,
                        selectedIds = recommendations.map { it.mediaId }.toSet()
                    )
                    applyFiltersAndSort(newState)
                }
            }
        }
    }

    fun selectCategory(category: CleanupCategory) {
        _uiState.update { state ->
            val newState = state.copy(selectedCategory = category, selectedIds = emptySet())
            applyFiltersAndSort(newState)
        }
    }

    fun toggleSelection(mediaId: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(mediaId)) {
                state.selectedIds - mediaId
            } else {
                state.selectedIds + mediaId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun selectAllInCategory() {
        _uiState.update { state ->
            val allIds = state.filteredRecommendations.map { it.mediaId }.toSet()
            state.copy(selectedIds = allIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun updateSort(sort: ReviewSort) {
        _uiState.update { state ->
            val newState = state.copy(currentSort = sort)
            applyFiltersAndSort(newState)
        }
    }

    fun requestDeleteSelected(
        context: android.content.Context,
        launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
    ) {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val items = selectedIds.mapNotNull { repository.getMediaItemById(it) }
        val recs = _uiState.value.recommendations.filter { selectedIds.contains(it.mediaId) }

        deleteManager.requestDeletion(context, items, recs, launcher)
    }

    fun keepSelected() {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val items = selectedIds.mapNotNull { repository.getMediaItemById(it) }
        val recs = _uiState.value.recommendations.filter { selectedIds.contains(it.mediaId) }

        items.forEach { item ->
            recs.find { it.mediaId == item.id }?.let { rec ->
                deleteManager.markAsKept(item, rec)
            }
        }
        
        clearSelection()
        loadRecommendations()
    }

    fun keepItem(mediaId: String) {
        val item = repository.getMediaItemById(mediaId) ?: return
        val rec = _uiState.value.recommendations.find { it.mediaId == mediaId } ?: return
        deleteManager.markAsKept(item, rec)
        loadRecommendations()
    }

    private fun applyFiltersAndSort(state: CleanupReviewUiState): CleanupReviewUiState {
        val filtered = state.recommendations.filter { it.category == state.selectedCategory }
        
        val sorted = when (state.currentSort) {
            ReviewSort.LOWEST_KEEP_SCORE -> filtered.sortedBy { it.keepScore }
            ReviewSort.HIGHEST_CONFIDENCE -> filtered.sortedByDescending { it.confidenceScore }
            ReviewSort.LARGEST_STORAGE -> filtered.sortedByDescending { it.storageSize }
            ReviewSort.MOST_EXPOSED -> filtered.sortedByDescending { it.exposureCount }
        }
        
        return state.copy(
            filteredRecommendations = sorted,
            storageRecoveryEstimate = sorted.sumOf { it.storageSize }
        )
    }
}

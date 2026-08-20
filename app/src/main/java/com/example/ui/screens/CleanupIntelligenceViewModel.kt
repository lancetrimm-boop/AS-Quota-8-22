package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.cleanup.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class CleanupIntelligenceUiState(
    val totalRecommendations: Int = 0,
    val forgottenCount: Int = 0,
    val neverConnectedCount: Int = 0,
    val spaceHogCount: Int = 0,
    val redundantCount: Int = 0,
    val potentialStorageRecovery: Long = 0L,
    val averageConfidence: Float = 0f,
    val averageKeepScore: Float = 0f,
    val lowestScoreItems: List<CleanupRecommendation> = emptyList(),
    val highestScoreItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val currentSort: CleanupSort = CleanupSort.LOWEST_KEEP_SCORE
)

enum class CleanupSort {
    LOWEST_KEEP_SCORE,
    HIGHEST_CONFIDENCE,
    LARGEST_STORAGE_IMPACT,
    CATEGORY
}

class CleanupIntelligenceViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupIntelligenceUiState())
    val uiState: StateFlow<CleanupIntelligenceUiState> = _uiState.asStateFlow()

    init {
        refreshAnalysis()
    }

    fun refreshAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // 1. Fetch all media items
            val allItems = repository.mediaItems.value
            if (allItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Perform heavy processing in background
            withContext(Dispatchers.Default) {
                // 2. Gather signals and calculate Keep Scores
                val tasteDNA = repository.tasteDNA.value
                val stats = repository.intelligenceStats.value
                
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
                            averageWatchDuration = if (item.viewCount > 0) 15f else 0f, // Heuristic for now
                            completionPercentage = item.progress,
                            skipCount = 0,
                            rating = item.rating,
                            isFavorite = item.isFavorite,
                            tasteAlignmentScore = evidence.exploitationScore,
                            contentHash = item.contentHash
                        )
                    )
                }

                // 3. Generate Recommendations
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

                // 4. Calculate Stats
                val recovery = CleanupRecommendationEngine.calculatePotentialRecovery(recommendations)
                val avgConf = if (recommendations.isNotEmpty()) recommendations.map { it.confidenceScore }.average().toFloat() else 0f
                val avgKeep = if (keepScoreResults.isNotEmpty()) keepScoreResults.map { it.keepScore }.average().toFloat() else 0f
                
                val sortedRecs = sortRecommendations(recommendations, _uiState.value.currentSort)
                
                // Protect high-value items list (Top 10 by Keep Score)
                val protectedItems = allItems.map { item ->
                    val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats)
                    val score = KeepScoreEngine.calculateScore(
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
                    ).keepScore
                    item to score
                }.sortedByDescending { it.second }.take(10).map { it.first }

                _uiState.update { state ->
                    state.copy(
                        totalRecommendations = recommendations.size,
                        forgottenCount = recommendations.count { it.category == CleanupCategory.FORGOTTEN },
                        neverConnectedCount = recommendations.count { it.category == CleanupCategory.NEVER_CONNECTED },
                        spaceHogCount = recommendations.count { it.category == CleanupCategory.SPACE_HOGS },
                        redundantCount = recommendations.count { it.category == CleanupCategory.REDUNDANT },
                        potentialStorageRecovery = recovery,
                        averageConfidence = avgConf,
                        averageKeepScore = avgKeep,
                        lowestScoreItems = sortedRecs.take(20),
                        highestScoreItems = protectedItems,
                        isLoading = false
                    )
                }

                // Structured validation signals (Internal only)
                println("CleanupIntelligence: Generated recommendations: ${recommendations.size}")
                println("CleanupIntelligence: Average confidence: ${(avgConf * 100).toInt()}%")
                println("CleanupIntelligence: Potential recovery: ${recovery / (1024 * 1024)} MB")
                println("CleanupIntelligence: Category distribution:")
                println("  FORGOTTEN: ${recommendations.count { it.category == CleanupCategory.FORGOTTEN }}")
                println("  SPACE_HOGS: ${recommendations.count { it.category == CleanupCategory.SPACE_HOGS }}")
                println("  REDUNDANT: ${recommendations.count { it.category == CleanupCategory.REDUNDANT }}")
            }
        }
    }

    fun updateSort(sort: CleanupSort) {
        _uiState.update { it.copy(currentSort = sort) }
        refreshAnalysis() // Re-sort and update
    }

    private fun sortRecommendations(recs: List<CleanupRecommendation>, sort: CleanupSort): List<CleanupRecommendation> {
        return when (sort) {
            CleanupSort.LOWEST_KEEP_SCORE -> recs.sortedBy { it.keepScore }
            CleanupSort.HIGHEST_CONFIDENCE -> recs.sortedByDescending { it.confidenceScore }
            CleanupSort.LARGEST_STORAGE_IMPACT -> recs.sortedByDescending { it.storageSize }
            CleanupSort.CATEGORY -> recs.sortedBy { it.category.name }
        }
    }
}

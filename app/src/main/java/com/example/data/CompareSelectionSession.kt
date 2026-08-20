package com.example.data

enum class CompareMediaTypeFilter {
    PHOTOS, VIDEOS
}

enum class CompareStrategy {
    PERSONALIZED, REDISCOVER, LEAST_INTERACTED, EXPLORE
}

enum class CompareSortOption {
    RECOMMENDED, LARGEST_FILES, SMALLEST_FILES, NEWEST, OLDEST
}

data class CompareSelectionSession(
    val isActive: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val originalCount: Int = 0,
    val roundNumber: Int = 0,
    val maxRounds: Int = 50,
    val wins: Int = 0,
    val losses: Int = 0,
    val skips: Int = 0,
    val comparedPairIds: List<Pair<String, String>> = emptyList(),
    val isComplete: Boolean = false,
    val completionReason: String? = null
)

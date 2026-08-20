package com.example.data

enum class MediaType {
    PHOTO, VIDEO
}

enum class CompatibilityStatus {
    PLAYABLE,
    PLAYABLE_SOFTWARE_DECODE,
    PLAYABLE_AFTER_CONVERSION,
    UNSUPPORTED,
    CORRUPT,
    UNREADABLE,
    ANALYSIS_PENDING,
    ANALYSIS_IN_PROGRESS,
    ANALYSIS_FAILED,
    THUMBNAIL_FAILED, // Playable but thumbnail generation failed
    UNTESTED,
    DELETED,
    NEEDS_TRANSCODE,
    REPLACED
}

enum class ConversionStatus {
    NONE,
    REQUIRED,
    IN_PROGRESS,
    CONVERTED,
    FAILED
}

data class MediaItem(
    val id: String,
    val title: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val year: Int = 2024,
    val duration: String = "",
    val genre: String = "Media",
    val imageUrl: String = "",
    val gradientColors: List<Long> = listOf(0xFF7C3AED, 0xFFD946EF),
    val rating: Float = 0f,
    val isFavorite: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val progressText: String = "", // e.g. "1:12:45 left"
    val category: String = "For You",
    val aiSummary: String = "",
    val moodTags: List<String> = emptyList(),
    val uriPath: String = "",
    val itemCount: Int? = null, // For collection cards e.g. "128 items"
    val sizeBytes: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val lastViewedTimestamp: Long? = null,
    val viewCount: Int = 0,
    val exposureCount: Int = 0,
    val lastExposedTimestamp: Long? = null,
    val dateModified: Long = 0L,
    val contentHash: String? = null,
    val parentContentId: String? = null,
    val eloRating: Double = 1500.0,
    val isDeleted: Boolean = false,
    val compatibilityStatus: CompatibilityStatus = CompatibilityStatus.PLAYABLE,
    val containerFormat: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val compatibilityReason: String = "",
    val conversionStatus: ConversionStatus = ConversionStatus.NONE,
    val convertedUri: String? = null,
    val lastCompatibilityCheckTimestamp: Long? = null,
    val selectionReason: String? = null,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val sourcePlatform: String? = "LOCAL", // e.g. "TIKTOK", "YOUTUBE", "INSTAGRAM", "PINTEREST"
    val replacedByMediaId: String? = null
)

data class PairwiseComparison(
    val id: String,
    val roundNumber: Int,
    val totalRounds: Int,
    val optionA: MediaItem,
    val optionB: MediaItem
)

data class IntelligenceStats(
    val personalizationScore: Int = 94,
    val totalComparisons: Int = 48,
    val itemsDiscovered: Int = 182,
    val topGenres: List<String> = listOf("Visual", "Video", "Photo"),
    val favoriteMoods: List<String> = listOf("Cinematic", "Vibrant", "Atmospheric")
)

data class ClipCandidate(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSec: Int,
    val relevanceScorePercent: Int,
    val selectionReason: String
)

enum class EmotionalRole {
    HIGH_CONFIDENCE,
    EXPLORATION,
    WILDCARD,
    EMERGING_INTEREST,
    DEEPENING
}

data class ObsessionRecommendation(
    val id: String,
    val title: String,
    val subtitle: String,
    val strategy: ObsessionStrategy,
    val previewItems: List<MediaItem>,
    val confidenceScore: Float,
    val explanation: RecommendationExplanation? = null,
    val emotionalRole: EmotionalRole = EmotionalRole.HIGH_CONFIDENCE
)

sealed class ObsessionStrategy {
    object Hero : ObsessionStrategy()
    object FavoriteRemix : ObsessionStrategy()
    object FreshArrivals : ObsessionStrategy()
    object HiddenGems : ObsessionStrategy()
    data class GenreFocus(val genre: String) : ObsessionStrategy()
    object NoveltyPulse : ObsessionStrategy()
    object DeepDiscovery : ObsessionStrategy()
}



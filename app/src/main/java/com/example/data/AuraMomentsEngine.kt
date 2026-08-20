package com.example.data

enum class MomentsMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String
) {
    FOR_YOU(
        id = "for_you",
        title = "For You",
        subtitle = "Personalized selection tuned to your taste",
        iconName = "sparkles"
    ),
    MEMORIES(
        id = "memories",
        title = "Memories",
        subtitle = "Recent and meaningful moments from your library",
        iconName = "history"
    ),
    SURPRISE_ME(
        id = "surprise_me",
        title = "Surprise Me",
        subtitle = "Hidden gems and unexpected discoveries",
        iconName = "shuffle"
    ),
    FAVORITES(
        id = "favorites",
        title = "Favorites",
        subtitle = "Your top-rated and favorited media",
        iconName = "favorite"
    ),
    AESTHETIC(
        id = "aesthetic",
        title = "Aesthetic",
        subtitle = "Visually cohesive sequences and mood harmonies",
        iconName = "palette"
    )
}

object AuraMomentsEngine {

    /**
     * Generates a curated, visually-sequenced slideshow playlist from [allMedia] based on [mode].
     * Degrades gracefully if metadata is sparse or library is small.
     */
    fun generateSlideshow(
        allMedia: List<MediaItem>,
        mode: MomentsMode,
        limit: Int = 20
    ): List<MediaItem> {
        // AURA PHASE 4: Filter to PHOTOS ONLY for Aura Moments
        val photosOnly = allMedia.filter { 
            it.mediaType.equals("PHOTO", ignoreCase = true) || it.mediaType.equals("Image", ignoreCase = true)
        }
        
        if (photosOnly.isEmpty()) return emptyList()

        val candidates = when (mode) {
            MomentsMode.FOR_YOU -> selectForYouCandidates(photosOnly, limit * 2)
            MomentsMode.MEMORIES -> selectMemoriesCandidates(photosOnly, limit * 2)
            MomentsMode.SURPRISE_ME -> selectSurpriseMeCandidates(photosOnly, limit * 2)
            MomentsMode.FAVORITES -> selectFavoritesCandidates(photosOnly, limit * 2)
            MomentsMode.AESTHETIC -> selectAestheticCandidates(photosOnly, limit * 2)
        }

        if (candidates.isEmpty()) {
            return sequenceVisualStory(photosOnly.take(limit))
        }

        val bounded = candidates.distinctBy { it.id }.take(limit.coerceAtLeast(1))
        return sequenceVisualStory(bounded)
    }

    private fun selectForYouCandidates(allMedia: List<MediaItem>, count: Int): List<MediaItem> {
        // Personalization signals: rating, favorite status, playCount, moodTags
        val scored = allMedia.map { item ->
            var score = 0.0
            if (item.rating > 0f) score += item.rating * 2.0
            if (item.isFavorite) score += 5.0
            score += (item.viewCount.coerceAtMost(10)) * 0.5
            if (item.moodTags.isNotEmpty()) score += 2.0
            item to score
        }
        return scored.sortedByDescending { it.second }.map { it.first }.take(count)
    }

    private fun selectMemoriesCandidates(allMedia: List<MediaItem>, count: Int): List<MediaItem> {
        // Prioritize recency (dateAdded / year) and meaningfulness (rating, favorites, microMoments)
        val meaningfulTags = setOf("nostalgic", "travel", "personal", "family", "memory", "vivid", "warm", "nature", "summer")
        val scored = allMedia.map { item ->
            var score = item.dateAdded.toDouble() / 1_000_000_000.0 // recency component
            if (item.year > 0) score += (item.year - 2000) * 1.0
            if (item.isFavorite) score += 4.0
            if (item.rating > 0f) score += item.rating
            if (item.moodTags.any { tag -> meaningfulTags.contains(tag.lowercase().trim()) }) {
                score += 3.0
            }
            item to score
        }
        return scored.sortedByDescending { it.second }.map { it.first }.take(count)
    }

    private fun selectSurpriseMeCandidates(allMedia: List<MediaItem>, count: Int): List<MediaItem> {
        // Less obvious items: lower playCount, moderate rating, or novel genres/tags
        val scored = allMedia.map { item ->
            var score = 0.0
            // Inverse play/view count bonus
            score += (10 - item.viewCount.coerceAtMost(10)) * 1.0
            if (item.genre.isNotBlank() && item.genre != "General") score += 2.0
            if (item.moodTags.isNotEmpty()) score += 1.5
            if (item.rating in 1.0f..3.5f) score += 2.0
            item to score
        }
        return scored.sortedByDescending { it.second }.map { it.first }.take(count)
    }

    private fun selectFavoritesCandidates(allMedia: List<MediaItem>, count: Int): List<MediaItem> {
        val favorited = allMedia.filter { it.isFavorite }
        if (favorited.isNotEmpty()) {
            return favorited.take(count)
        }
        // Graceful fallback if no favorites exist
        return allMedia.sortedByDescending { it.rating }.take(count)
    }

    private fun selectAestheticCandidates(allMedia: List<MediaItem>, count: Int): List<MediaItem> {
        val aestheticTags = setOf(
            "vivid", "warm", "cool", "cinematic", "minimal", "minimalist",
            "monochrome", "vibrant", "crisp", "smooth", "spacious", "tactile"
        )
        val (richAesthetic, standard) = allMedia.partition { item ->
            item.moodTags.any { tag -> aestheticTags.contains(tag.lowercase().trim()) } || item.genre.isNotBlank()
        }

        val grouped = richAesthetic.groupBy { item ->
            item.moodTags.firstOrNull { aestheticTags.contains(it.lowercase().trim()) } ?: item.genre
        }

        val result = mutableListOf<MediaItem>()
        grouped.values.forEach { group ->
            result.addAll(group)
        }
        result.addAll(standard)
        return result.take(count)
    }

    /**
     * Applies deterministic visual story sequencing to prevent consecutive repetitive items
     * and produce a cohesive visual flow:
     * Structure: Opening -> Related -> Visual Variation -> High-Confidence -> Closing
     */
    fun sequenceVisualStory(items: List<MediaItem>): List<MediaItem> {
        if (items.size <= 1) return items.distinctBy { it.id }

        val uniqueItems = items.distinctBy { it.id }.toMutableList()
        if (uniqueItems.size <= 1) return uniqueItems

        val sequenced = mutableListOf<MediaItem>()

        // 1. Opening establishing item
        val opening = uniqueItems.removeAt(0)
        sequenced.add(opening)

        var lastItem = opening

        // 2. Interleave items to avoid consecutive identical category / genre
        while (uniqueItems.isNotEmpty()) {
            val candidateIndex = uniqueItems.indexOfFirst { cand ->
                val categoryDiffers = cand.category.isBlank() || !cand.category.equals(lastItem.category, ignoreCase = true)
                val genreDiffers = cand.genre.isBlank() || !cand.genre.equals(lastItem.genre, ignoreCase = true)
                categoryDiffers || genreDiffers
            }

            val nextIndex = if (candidateIndex >= 0) candidateIndex else 0
            val nextItem = uniqueItems.removeAt(nextIndex)
            sequenced.add(nextItem)
            lastItem = nextItem
        }

        return sequenced
    }
}
